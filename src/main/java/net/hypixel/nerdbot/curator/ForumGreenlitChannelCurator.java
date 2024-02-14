package net.hypixel.nerdbot.curator;

import io.prometheus.client.Summary;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ForumGreenlitChannelCurator extends Curator<ForumChannel> {

    public ForumGreenlitChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    public static GreenlitMessage createGreenlitMessage(ForumChannel forumChannel, Message message, ThreadChannel thread, int agree, int neutral, int disagree) {
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

        return GreenlitMessage.builder()
            .agrees(agree)
            .neutrals(neutral)
            .disagrees(disagree)
            .messageId(message.getId())
            .userId(message.getAuthor().getId())
            .alpha(forumChannel.getName().toLowerCase().contains("alpha"))
            .suggestionUrl(message.getJumpUrl())
            .suggestionTitle(thread.getName())
            .suggestionTimestamp(thread.getTimeCreated().toInstant().toEpochMilli())
            .suggestionContent(message.getContentRaw())
            .tags(thread.getAppliedTags().stream().map(BaseForumTag::getName).toList())
            .positiveVoterIds(
                message.getReactions().stream()
                    .filter(reaction -> suggestionConfig.isReactionEquals(reaction, SuggestionConfig::getAgreeEmojiId))
                    .flatMap(reaction -> reaction.retrieveUsers()
                        .complete()
                        .stream()
                    )
                    .map(User::getId)
                    .toList()
            )
            .build();
    }

    @Override
    public List<GreenlitMessage> curate(ForumChannel forumChannel) {
        setStartTime(System.currentTimeMillis());

        try (Summary.Timer timer = PrometheusMetrics.CURATOR_LENGTH_SECONDS.labels(forumChannel.getName()).startTimer()) {
            List<GreenlitMessage> output = new ArrayList<>();
            Database database = NerdBotApp.getBot().getDatabase();

            if (!database.isConnected()) {
                setEndTime(System.currentTimeMillis());
                timer.observeDuration();
                log.error("Couldn't export greenlit suggestions as the database is not connected!");
                return output;
            }

            BotConfig config = NerdBotApp.getBot().getConfig();
            SuggestionConfig suggestionConfig = config.getSuggestionConfig();

            if (suggestionConfig == null) {
                log.error("Couldn't find the emoji config from the bot config!");
                timer.observeDuration();
                return output;
            }

            ForumTag greenlitTag = Util.getTagByName(forumChannel, suggestionConfig.getGreenlitTag());

            if (greenlitTag == null) {
                log.error("Couldn't find the greenlit tag for the forum channel " + forumChannel.getName() + " (ID: " + forumChannel.getId() + ")!");
                timer.observeDuration();
                return output;
            }

            log.info("Exporting greenlit suggestions for forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

            List<ThreadChannel> threads = Stream.concat(
                    forumChannel.getThreadChannels().stream(), // Unarchived Posts
                    forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
                )
                .distinct()
                .toList();

            log.info("Found " + threads.size() + " forum post(s)!");
            PrometheusMetrics.CURATOR_MESSAGES_AMOUNT.labels(forumChannel.getName()).inc(threads.size());

            int index = 0;
            for (ThreadChannel thread : threads) {
                log.info("[" + (++index) + "/" + threads.size() + "] Exporting thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");

                List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());

                if (!tags.contains(greenlitTag)) {
                    continue;
                }

                log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has the greenlit tag, adding to the list.");

                MessageHistory history = thread.getHistoryFromBeginning(1).complete();
                Message message = history.isEmpty() ? null : history.getRetrievedHistory().get(0);

                if (message == null) {
                    log.error("Message for thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is null!");
                    continue;
                }

                try {
                    log.info("Checking reaction counts for message ID: " + message.getId());

                    List<MessageReaction> reactions = message.getReactions()
                        .stream()
                        .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                        .toList();

                    Map<String, Integer> votes = Stream.of(
                            suggestionConfig.getAgreeEmojiId(),
                            suggestionConfig.getNeutralEmojiId(),
                            suggestionConfig.getDisagreeEmojiId()
                        )
                        .map(emojiId -> Pair.of(
                            emojiId,
                            reactions.stream()
                                .filter(reaction -> reaction.getEmoji()
                                    .asCustom()
                                    .getId()
                                    .equalsIgnoreCase(emojiId)
                                )
                                .mapToInt(MessageReaction::getCount)
                                .findFirst()
                                .orElse(0)
                        ))
                        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

                    int agree = votes.get(suggestionConfig.getAgreeEmojiId());
                    int neutral = votes.get(suggestionConfig.getNeutralEmojiId());
                    int disagree = votes.get(suggestionConfig.getDisagreeEmojiId());

                    GreenlitMessage greenlitMessage = createGreenlitMessage(forumChannel, message, thread, agree, neutral, disagree);
                    output.add(greenlitMessage);
                } catch (Exception exception) {
                    log.error("Failed to export greenlit thread '" + thread.getName() + "' (ID: " + thread.getId() + ")!", exception);
                }
            }

            setEndTime(System.currentTimeMillis());
            timer.observeDuration();
            log.info("Finished exporting greenlit suggestions for forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");
            return output;
        }
    }
}
