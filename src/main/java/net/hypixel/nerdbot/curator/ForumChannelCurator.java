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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel> {

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
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
                log.error("Couldn't curate messages as the database is not connected!");
                return output;
            }

            BotConfig config = NerdBotApp.getBot().getConfig();
            SuggestionConfig suggestionConfig = config.getSuggestionConfig();

            if (suggestionConfig == null) {
                log.error("Couldn't find the emoji config from the bot config!");
                timer.observeDuration();
                return output;
            }

            ForumTag greenlitTag = forumChannel.getAvailableTagById(suggestionConfig.getGreenlitTag());

            if (greenlitTag == null) {
                log.error("Couldn't find the greenlit tag for the forum channel " + forumChannel.getName() + " (ID: " + forumChannel.getId() + ")!");
                timer.observeDuration();
                return output;
            }

            log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

            List<ThreadChannel> threads = Stream.concat(
                    forumChannel.getThreadChannels().stream(), // Unarchived Posts
                    forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
                )
                .filter(thread -> !thread.isLocked() && !thread.isArchived())
                .distinct()
                .toList();

            log.info("Found " + threads.size() + " forum post(s)!");
            PrometheusMetrics.CURATOR_MESSAGES_AMOUNT.labels(forumChannel.getName()).inc(threads.size());

            int index = 0;
            for (ThreadChannel thread : threads) {
                log.info("[" + (++index) + "/" + threads.size() + "] Curating thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");

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
                    List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                    ThreadChannelManager threadManager = thread.getManager();

                    // Upsert into database if already greenlit
                    if (tags.stream().anyMatch(tag -> tag.getId().equals(suggestionConfig.getGreenlitTag()) || tag.getId().equals(suggestionConfig.getReviewedTag()))) {
                        log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is already greenlit/reviewed!");
                        GreenlitMessage greenlitMessage = createGreenlitMessage(forumChannel, message, thread, agree, neutral, disagree);
                        database.getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                        continue;
                    }

                    double ratio = getRatio(agree, disagree);
                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has " + agree + " agree reactions, " + neutral + " neutral reactions, and " + disagree + " disagree reactions with a ratio of " + ratio + "%");

                    if ((agree < suggestionConfig.getGreenlitThreshold()) || (ratio < suggestionConfig.getGreenlitRatio())) {
                        log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") does not meet the minimum threshold of " + suggestionConfig.getGreenlitThreshold() + " agree reactions to be greenlit!");
                        continue;
                    }

                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") meets the minimum requirements to be greenlit!");

                    if (isReadOnly()) {
                        log.info("Skipping thread '" + thread.getName() + "' (ID: " + thread.getId() + ") as the curator is in read-only mode!");
                        continue;
                    }

                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has tags: " + thread.getAppliedTags().stream().map(BaseForumTag::getName).toList());

                    if (!tags.contains(greenlitTag)) {
                        tags.add(greenlitTag);
                    }

                    boolean wasArchived = thread.isArchived();

                    if (wasArchived) {
                        threadManager = threadManager.setArchived(false);
                    }

                    threadManager = threadManager.setAppliedTags(tags);

                    // Handle Archiving and Locking
                    if (wasArchived || suggestionConfig.isArchiveOnGreenlit()) {
                        threadManager = threadManager.setArchived(true);
                    }

                    if (suggestionConfig.isLockOnGreenlit()) {
                        threadManager = threadManager.setLocked(true);
                    }

                    // Send Changes
                    threadManager.queue();

                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has been greenlit!");
                    GreenlitMessage greenlitMessage = createGreenlitMessage(forumChannel, message, thread, agree, neutral, disagree);
                    NerdBotApp.getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                    output.add(greenlitMessage);
                } catch (Exception e) {
                    log.error("Failed to curate thread '" + thread.getName() + "' (ID: " + thread.getId() + ")!", e);
                }
            }

            setEndTime(System.currentTimeMillis());
            timer.observeDuration();
            log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");
            return output;
        }
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
}
