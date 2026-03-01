package net.hypixel.nerdbot.app.curator;

import io.prometheus.client.Summary;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
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
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.app.curator.Curator;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.EmojiConfigUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
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
                    .filter(reaction -> EmojiConfigUtils.isReactionEquals(DiscordBotEnvironment.getBot().getConfig().getEmojiConfig(), reaction, EmojiConfig::getAgreeEmojiId))
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
            Database database = BotEnvironment.getBot().getDatabase();

            if (!database.isConnected()) {
                setEndTime(System.currentTimeMillis());
                timer.observeDuration();
                log.error("Couldn't curate messages as the database is not connected!");
                return output;
            }

            DiscordBotConfig botConfig = DiscordBotEnvironment.getBot().getConfig();
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

            ForumTag greenlitTag = DiscordUtils.getTagByName(forumChannel, suggestionConfig.getGreenlitTag());

            if (greenlitTag == null) {
                log.error("Couldn't find the greenlit tag for the forum channel {} (ID: {})!", forumChannel.getName(), forumChannel.getId());
                timer.observeDuration();
                return output;
            }

            if (emojiConfig.getGreenlitEmojiId() == null || emojiConfig.getAgreeEmojiId() == null || emojiConfig.getNeutralEmojiId() == null || emojiConfig.getDisagreeEmojiId() == null
                || EmojiCache.getEmojiById(emojiConfig.getGreenlitEmojiId()).isEmpty() || EmojiCache.getEmojiById(emojiConfig.getAgreeEmojiId()).isEmpty()
                || EmojiCache.getEmojiById(emojiConfig.getNeutralEmojiId()).isEmpty() || EmojiCache.getEmojiById(emojiConfig.getDisagreeEmojiId()).isEmpty()) {
                log.error("Couldn't find the greenlit, agree, neutral, or disagree emoji in the channel {} (ID: {})! Check the emojiConfig values!", forumChannel.getName(), forumChannel.getId());
                timer.observeDuration();
                return output;
            }

            log.info("Curating forum channel: {} (Channel ID: {})", forumChannel.getName(), forumChannel.getId());

            long start = System.currentTimeMillis();
            List<ThreadChannel> threads = Stream.concat(
                    forumChannel.getThreadChannels().stream(), // Unarchived Posts
                    forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
                )
                .filter(thread -> !thread.isLocked() && !thread.isArchived())
                .distinct()
                .toList();

            log.info("Found {} forum post(s) in {}ms", threads.size(), System.currentTimeMillis() - start);
            PrometheusMetrics.CURATOR_MESSAGES_AMOUNT.labels(forumChannel.getName()).inc(threads.size());
            setIndex(0);
            setTotal(threads.size());

            for (ThreadChannel thread : threads) {
                setIndex(getIndex() + 1);
                setCurrentObject(thread);

                log.info("[{}/{}] Curating thread '{}' (ID: {})", getIndex(), threads.size(), thread.getName(), thread.getId());

                MessageHistory history = thread.getHistoryFromBeginning(1).complete();
                Message message = history.isEmpty() ? null : history.getRetrievedHistory().get(0);

                if (message == null) {
                    log.error("Message for thread '{}' (ID: {}) is null!", thread.getName(), thread.getId());
                    continue;
                }

                try {
                    log.info("Checking reaction counts for message ID: {}", message.getId());

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
                    if (DiscordUtils.hasTagByName(thread, suggestionConfig.getGreenlitTag())) {
                        log.info("Thread '{}' (ID: {}) is already greenlit/reviewed!", thread.getName(), thread.getId());
                        GreenlitMessage greenlitMessage = createGreenlitMessage(message, thread, agree, neutral, disagree);
                        database.getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                        continue;
                    }

                    double ratio = getRatio(agree, disagree);
                    log.info("Thread '{}' (ID: {}) has {} agree reactions, {} neutral reactions, and {} disagree reactions with a ratio of {}%", thread.getName(), thread.getId(), agree, neutral, disagree, ratio);

                    if ((agree < suggestionConfig.getGreenlitThreshold()) || (ratio < suggestionConfig.getGreenlitRatio())) {
                        log.info("Thread '{}' (ID: {}) does not meet the minimum threshold of {} agree reactions to be greenlit!", thread.getName(), thread.getId(), suggestionConfig.getGreenlitThreshold());
                        continue;
                    }

                    log.info("Thread '{}' (ID: {}) meets the minimum requirements to be greenlit!", thread.getName(), thread.getId());

                    if (isReadOnly()) {
                        log.info("Skipping thread '{}' (ID: {}) as the curator is in read-only mode!", thread.getName(), thread.getId());
                        continue;
                    }

                    log.info("Thread '{}' (ID: {}) has tags: {}", thread.getName(), thread.getId(), thread.getAppliedTags().stream().map(BaseForumTag::getName).toList());

                    // Discord only allows a maximum of 5 tags per thread
                    if (tags.size() >= 5 && !tags.contains(greenlitTag)) {
                        log.warn("Thread '{}' (ID: {}) already has the maximum of 5 tags. Cannot apply greenlit tag.", thread.getName(), thread.getId());
                        thread.sendMessageEmbeds(new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Error")
                            .setDescription("This suggestion could not be automatically greenlit because the thread already has the maximum number of tags (5). Please remove a tag to allow the greenlit tag to be applied.")
                            .build()
                        ).setContent("<@" + thread.getOwnerId() + ">").queue();
                        continue;
                    }

                    if (!tags.contains(greenlitTag)) {
                        tags.add(greenlitTag);
                    }

                    ForumAutoTag autoTagConfig = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getForumAutoTagConfig(forumChannel.getId());
                    if (autoTagConfig != null && autoTagConfig.getReviewTagName().equalsIgnoreCase(suggestionConfig.getGreenlitTag())) {
                        // Remove the default tag if it exists
                        ForumTag defaultTag = DiscordUtils.getTagByName(forumChannel, autoTagConfig.getDefaultTagName());
                        if (defaultTag != null && tags.contains(defaultTag)) {
                            tags.remove(defaultTag);
                            log.info("Removed auto-tag '{}' from thread '{}' (ID: {}) when greenlit via curator", autoTagConfig.getDefaultTagName(), thread.getName(), thread.getId());
                        }
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

                    log.info("Thread '{}' (ID: {}) has been greenlit!", thread.getName(), thread.getId());
                    GreenlitMessage greenlitMessage = createGreenlitMessage(message, thread, agree, neutral, disagree);
                    DiscordBotEnvironment.getBot().getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                    output.add(greenlitMessage);
                } catch (Exception exception) {
                    log.error("Failed to curate thread '{}' (ID: {})!", thread.getName(), thread.getId(), exception);
                }
            }

            setEndTime(System.currentTimeMillis());
            timer.observeDuration();
            log.info("Curated forum channel: {} (Channel ID: {}) in {}ms", forumChannel.getName(), forumChannel.getId(), getEndTime() - getStartTime());
            setCompleted(true);
            setCurrentObject(null);
            setIndex(0);

            return output;
        }
    }
}