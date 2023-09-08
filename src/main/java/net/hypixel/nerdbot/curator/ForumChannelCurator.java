package net.hypixel.nerdbot.curator;

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
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel> {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    @Override
    public List<GreenlitMessage> curate(ForumChannel forumChannel) {
        setStartTime(System.currentTimeMillis());

        List<GreenlitMessage> output = new ArrayList<>();
        Database database = NerdBotApp.getBot().getDatabase();

        if (!database.isConnected()) {
            log.error("Couldn't curate messages as the database is not connected!");
            return output;
        }

        BotConfig config = NerdBotApp.getBot().getConfig();
        EmojiConfig emojiConfig = config.getEmojiConfig();

        if (emojiConfig == null) {
            log.error("Couldn't find the emoji config from the bot config!");
            return output;
        }

        ForumTag greenlitTag = forumChannel.getAvailableTagsByName("greenlit", true).get(0);

        if (greenlitTag == null) {
            log.error("Couldn't find the greenlit tag for the forum channel " + forumChannel.getName() + " (ID: " + forumChannel.getId() + ")!");
            return output;
        }

        if (!database.isConnected()) {
            setEndTime(System.currentTimeMillis());
            log.error("Couldn't curate messages as the database is not connected!");
            return output;
        }

        log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

        List<ThreadChannel> threads = Stream.concat(
                forumChannel.getThreadChannels().stream(), // Unarchived Posts
                forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
            )
            .distinct()
            .filter(threadChannel -> threadChannel.getAppliedTags()
                .stream()
                .noneMatch(tag -> GREENLIT_TAGS.contains(tag.getName().toLowerCase()))
            )
            .toList();

        log.info("Found " + threads.size() + " non-greenlit/docced forum post(s)!");

        int index = 0;
        for (ThreadChannel thread : threads) {
            log.info("[" + (++index) + "/" + threads.size() + "] Curating thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");

            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);

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
                        emojiConfig.getAgreeEmojiId(),
                        emojiConfig.getNeutralEmojiId(),
                        emojiConfig.getDisagreeEmojiId()
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

                int agree = votes.get(emojiConfig.getAgreeEmojiId());
                int neutral = votes.get(emojiConfig.getNeutralEmojiId());
                int disagree = votes.get(emojiConfig.getDisagreeEmojiId());
                List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());

                // Upsert into database if already greenlit
                if (tags.stream().anyMatch(tag -> GREENLIT_TAGS.contains(tag.getName()))) {
                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is already greenlit/docced!");
                    GreenlitMessage greenlitMessage = createGreenlitMessage(forumChannel, message, thread, agree, neutral, disagree);
                    database.upsertDocument(database.getCollection("greenlit_messages", GreenlitMessage.class), "messageId", message.getId(), greenlitMessage);
                    continue;
                }

                double ratio = getRatio(agree, disagree);
                log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has " + agree + " agree reactions, " + neutral + " neutral reactions, and " + disagree + " disagree reactions with a ratio of " + ratio + "%");

                if ((agree < config.getMinimumThreshold()) || (ratio < config.getPercentage())) {
                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") does not meet the minimum requirements to be greenlit!");
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

                boolean archived = thread.isArchived();

                if (archived) {
                    thread.getManager().setArchived(false).complete();
                }

                thread.getManager().setAppliedTags(tags).complete();

                if (archived) {
                    thread.getManager().setArchived(true).queue();
                }

                GreenlitMessage greenlitMessage = createGreenlitMessage(forumChannel, message, thread, agree, neutral, disagree);
                output.add(greenlitMessage);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        setEndTime(System.currentTimeMillis());
        log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");

        return output;
    }

    private GreenlitMessage createGreenlitMessage(ForumChannel forumChannel, Message message, ThreadChannel thread, int agree, int neutral, int disagree) {
        EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();

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
                    .filter(reaction -> emojiConfig.isEquals(reaction, EmojiConfig::getAgreeEmojiId))
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
