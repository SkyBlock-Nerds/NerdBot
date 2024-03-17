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
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel, ThreadChannel> {

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    public static GreenlitMessage createGreenlitMessage(Message message, ThreadChannel thread, int agree, int neutral, int disagree) {
        return GreenlitMessage.builder()
            .agrees(agree)
            .neutrals(neutral)
            .disagrees(disagree)
            .messageId(message.getId())
            .userId(message.getAuthor().getId())
            .suggestionUrl(message.getJumpUrl())
            .suggestionTitle(thread.getName())
            .suggestionTimestamp(thread.getTimeCreated().toInstant().toEpochMilli())
            .suggestionContent(message.getContentRaw())
            .tags(thread.getAppliedTags().stream().map(BaseForumTag::getName).toList())
            .positiveVoterIds(
                message.getReactions().stream()
                    .filter(reaction -> NerdBotApp.getBot().getConfig().getEmojiConfig().isReactionEquals(reaction, EmojiConfig::getAgreeEmojiId))
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
                log.error("Couldn't curate messages as the database is not connected!");
                return output;
            }

            BotConfig botConfig = NerdBotApp.getBot().getConfig();
            SuggestionConfig suggestionConfig = botConfig.getSuggestionConfig();

            if (suggestionConfig == null) {
                log.error("Couldn't find the suggestion config from the bot config!");
                timer.observeDuration();
                return output;
            }

            EmojiConfig emojiConfig = botConfig.getEmojiConfig();

            if (emojiConfig == null) {
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

            if (emojiConfig.getGreenlitEmojiId() == null || emojiConfig.getAgreeEmojiId() == null || emojiConfig.getNeutralEmojiId() == null || emojiConfig.getDisagreeEmojiId() == null
                || EmojiCache.getEmojiById(emojiConfig.getGreenlitEmojiId()).isEmpty() || EmojiCache.getEmojiById(emojiConfig.getAgreeEmojiId()).isEmpty()
                || EmojiCache.getEmojiById(emojiConfig.getNeutralEmojiId()).isEmpty() || EmojiCache.getEmojiById(emojiConfig.getDisagreeEmojiId()).isEmpty()) {
                log.error("Couldn't find the greenlit, agree, neutral, or disagree emoji in the channel " + forumChannel.getName() + " (ID: " + forumChannel.getId() + ")! Check the emojiConfig values!");
                timer.observeDuration();
                return output;
            }

            log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

            long start = System.currentTimeMillis();
            List<ThreadChannel> threads = Stream.concat(
                    forumChannel.getThreadChannels().stream(), // Unarchived Posts
                    forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
                )
                .filter(thread -> !thread.isLocked() && !thread.isArchived())
                .distinct()
                .toList();

            log.info("Found " + threads.size() + " forum post(s) in " + (System.currentTimeMillis() - start) + "ms");
            PrometheusMetrics.CURATOR_MESSAGES_AMOUNT.labels(forumChannel.getName()).inc(threads.size());
            setIndex(0);
            setTotal(threads.size());

            for (ThreadChannel thread : threads) {
                setIndex(getIndex() + 1);
                setCurrentObject(thread);

                log.info("[" + getIndex() + "/" + threads.size() + "] Curating thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");

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
                    ThreadChannelManager threadManager = thread.getManager();

                    // Upsert into database if already greenlit
                    if (Util.hasTagByName(thread, suggestionConfig.getGreenlitTag())) {
                        log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is already greenlit/reviewed!");
                        GreenlitMessage greenlitMessage = createGreenlitMessage(message, thread, agree, neutral, disagree);
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
                    GreenlitMessage greenlitMessage = createGreenlitMessage(message, thread, agree, neutral, disagree);
                    NerdBotApp.getBot().getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                    output.add(greenlitMessage);
                } catch (Exception exception) {
                    log.error("Failed to curate thread '" + thread.getName() + "' (ID: " + thread.getId() + ")!", exception);
                }
            }

            setEndTime(System.currentTimeMillis());
            timer.observeDuration();
            log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");
            setCompleted(true);
            setCurrentObject(null);
            setIndex(0);

            return output;
        }
    }
}
