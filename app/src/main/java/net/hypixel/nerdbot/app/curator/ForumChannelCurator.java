package net.hypixel.nerdbot.app.curator;

import io.prometheus.client.Summary;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
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
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.SuggestionConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.storage.GreenlitStore;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.EmojiConfigUtils;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Greenlights the suggestions in a forum with enough votes, and stores every greenlit suggestion's
 * latest votes. One curation runs at a time, whether it was scheduled or started with
 * {@code /curate}, so the same suggestion can't be greenlit twice at once.
 */
@Slf4j
@Singleton
public class ForumChannelCurator {

    // Discord allows a forum post at most 5 tags
    private static final int MAX_TAGS = 5;

    private final Provider<NerdBotConfig> config;
    private final GreenlitStore greenlit;
    private final SuggestionCache suggestions;
    private final Provider<BotState> state;
    private final Clock clock;
    private final ReentrantLock running = new ReentrantLock();

    @Inject
    public ForumChannelCurator(Provider<NerdBotConfig> config, GreenlitStore greenlit, SuggestionCache suggestions, Provider<BotState> state, Clock clock) {
        this.config = config;
        this.greenlit = greenlit;
        this.suggestions = suggestions;
        this.state = state;
        this.clock = clock;
    }

    public static double getRatio(double positiveReactions, double negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }
        return positiveReactions / (positiveReactions + negativeReactions) * 100;
    }

    /**
     * Whether a suggestion's votes qualify it to be greenlit: it needs at least {@code agreeThreshold}
     * agree reactions <em>and</em> an agree/disagree {@link #getRatio(double, double) ratio} of at
     * least {@code ratioThreshold} percent.
     */
    public static boolean meetsGreenlitThreshold(int agree, int disagree, int agreeThreshold, double ratioThreshold) {
        return agree >= agreeThreshold && getRatio(agree, disagree) >= ratioThreshold;
    }

    /**
     * Fetches every agree voter, page by page, so this blocks for a while on popular suggestions; call
     * it off JDA's threads.
     */
    public static GreenlitMessage createGreenlitMessage(Message message, ThreadChannel thread, int agree, int neutral, int disagree, EmojiConfig emojiConfig) {
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
                    .filter(reaction -> EmojiConfigUtils.isReactionEquals(emojiConfig, reaction, EmojiConfig::getAgreeEmojiId))
                    // complete() would only return the first page, at most 100 voters; stream() fetches them all
                    .flatMap(reaction -> reaction.retrieveUsers().stream())
                    .map(User::getId)
                    .toList()
            )
            .build();
    }

    /**
     * Curates the forum's open posts, recording progress in {@code curation} as it goes. In read-only
     * mode every curation is a dry run.
     *
     * @return false, without curating, when another curation is already running
     */
    public boolean curate(ForumChannel forum, Curation curation) {
        if (!running.tryLock()) {
            log.warn("Not curating '{}'; another curation is still running", forum.getName());
            return false;
        }

        try (Summary.Timer timer = PrometheusMetrics.CURATOR_LENGTH_SECONDS.labels(forum.getName()).startTimer()) {
            curateOpenPosts(forum, curation, curation.isDryRun() || state.get().isReadOnly());
            return true;
        } finally {
            curation.finish(clock.millis());
            running.unlock();
        }
    }

    private void curateOpenPosts(ForumChannel forum, Curation curation, boolean dryRun) {
        NerdBotConfig current = config.get();
        SuggestionConfig suggestionConfig = current.getSuggestionConfig();
        EmojiConfig emojiConfig = current.getEmojiConfig();
        List<ThreadChannel> threads = forum.getThreadChannels().stream()
            .filter(thread -> !thread.isLocked() && !thread.isArchived())
            .toList();
        curation.start(clock.millis(), threads.size());

        Optional<ForumTag> greenlitTag = DiscordUtils.findTagByName(forum, suggestionConfig.getGreenlitTag());
        if (greenlitTag.isEmpty()) {
            log.error("Not curating '{}' ({}); it has no '{}' tag", forum.getName(), forum.getId(), suggestionConfig.getGreenlitTag());
            return;
        }
        if (!votingEmojisExist(emojiConfig)) {
            log.error("Not curating '{}' ({}); the greenlit, agree, neutral or disagree emoji in the emoji config can't be found", forum.getName(), forum.getId());
            return;
        }

        log.info("Curating {} open posts in '{}' ({}){}", threads.size(), forum.getName(), forum.getId(), dryRun ? " without changing anything" : "");
        PrometheusMetrics.CURATOR_MESSAGES_AMOUNT.labels(forum.getName()).inc(threads.size());

        for (int index = 0; index < threads.size(); index++) {
            ThreadChannel thread = threads.get(index);
            curation.moveTo(index + 1, thread);
            try {
                curateThread(forum, thread, greenlitTag.get(), current, dryRun).ifPresent(message -> {
                    curation.addGreenlit(message);
                    PrometheusMetrics.TOTAL_GREENLIT_MESSAGES_AMOUNT.inc();
                    PrometheusMetrics.GREENLIT_SUGGESTION_LENGTH.observe(message.getSuggestionContent().length());
                });
            } catch (RuntimeException exception) {
                log.error("Failed to curate thread '{}' ({}); carrying on with the rest", thread.getName(), thread.getId(), exception);
            }
        }

        log.info("Curated '{}' ({}): greenlit {} of {} posts", forum.getName(), forum.getId(), curation.greenlit().size(), threads.size());
    }

    private static boolean votingEmojisExist(EmojiConfig emojiConfig) {
        return Stream.of(emojiConfig.getGreenlitEmojiId(), emojiConfig.getAgreeEmojiId(), emojiConfig.getNeutralEmojiId(), emojiConfig.getDisagreeEmojiId())
            .allMatch(id -> id != null && EmojiCache.getEmojiById(id).isPresent());
    }

    /** The newly greenlit suggestion, if this thread was greenlit now. */
    private Optional<GreenlitMessage> curateThread(ForumChannel forum, ThreadChannel thread, ForumTag greenlitTag, NerdBotConfig current, boolean dryRun) {
        SuggestionConfig suggestionConfig = current.getSuggestionConfig();
        EmojiConfig emojiConfig = current.getEmojiConfig();

        Optional<Message> firstMessage = firstMessageOf(thread);
        if (firstMessage.isEmpty()) {
            log.warn("Thread '{}' ({}) has no first message", thread.getName(), thread.getId());
            return Optional.empty();
        }

        Message message = firstMessage.get();
        int agree = countReactions(message, emojiConfig.getAgreeEmojiId());
        int neutral = countReactions(message, emojiConfig.getNeutralEmojiId());
        int disagree = countReactions(message, emojiConfig.getDisagreeEmojiId());

        if (DiscordUtils.hasTagByName(thread, suggestionConfig.getGreenlitTag())) {
            if (!dryRun) {
                // Already greenlit, so only its votes are brought up to date
                save(describe(message, thread, agree, neutral, disagree, emojiConfig));
            }
            return Optional.empty();
        }

        log.debug("Thread '{}' ({}) has {} agrees, {} neutrals and {} disagrees ({}%)",
            thread.getName(), thread.getId(), agree, neutral, disagree, getRatio(agree, disagree));
        if (!meetsGreenlitThreshold(agree, disagree, suggestionConfig.getGreenlitThreshold(), suggestionConfig.getGreenlitRatio())) {
            return Optional.empty();
        }

        if (dryRun) {
            log.info("Thread '{}' ({}) would be greenlit", thread.getName(), thread.getId());
            return Optional.empty();
        }

        List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
        if (tags.size() >= MAX_TAGS) {
            log.warn("Thread '{}' ({}) already has {} tags, so the greenlit tag can't be added", thread.getName(), thread.getId(), MAX_TAGS);
            thread.sendMessageEmbeds(EmbedFactory.error("Error",
                    "This suggestion could not be automatically greenlit because the thread already has the maximum number of tags (5). Please remove a tag to allow the greenlit tag to be applied.").build())
                .setContent("<@" + thread.getOwnerId() + ">")
                .queue();
            return Optional.empty();
        }

        tags.add(greenlitTag);
        removeDefaultTag(forum, tags, current);
        ThreadChannelManager manager = thread.getManager().setAppliedTags(tags);
        if (suggestionConfig.isArchiveOnGreenlit()) {
            manager = manager.setArchived(true);
        }
        if (suggestionConfig.isLockOnGreenlit()) {
            manager = manager.setLocked(true);
        }
        manager.complete();

        GreenlitMessage greenlitMessage = describe(message, thread, agree, neutral, disagree, emojiConfig);
        save(greenlitMessage);
        suggestions.requestUpdate(thread);
        log.info("Greenlit thread '{}' ({})", thread.getName(), thread.getId());
        return Optional.of(greenlitMessage);
    }

    // This and describe are overridden in tests, which can't build JDA's message history or voter lists
    Optional<Message> firstMessageOf(ThreadChannel thread) {
        MessageHistory history = thread.getHistoryFromBeginning(1).complete();
        return history.isEmpty() ? Optional.empty() : Optional.of(history.getRetrievedHistory().getFirst());
    }

    GreenlitMessage describe(Message message, ThreadChannel thread, int agree, int neutral, int disagree, EmojiConfig emojiConfig) {
        return createGreenlitMessage(message, thread, agree, neutral, disagree, emojiConfig);
    }

    private static int countReactions(Message message, String emojiId) {
        return message.getReactions().stream()
            .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
            .filter(reaction -> reaction.getEmoji().asCustom().getId().equals(emojiId))
            .mapToInt(MessageReaction::getCount)
            .findFirst()
            .orElse(0);
    }

    // When the greenlit tag is the forum's review tag, the default tag it replaces comes off
    private static void removeDefaultTag(ForumChannel forum, List<ForumTag> tags, NerdBotConfig current) {
        ForumAutoTag autoTag = current.getChannelConfig().getForumAutoTagConfig(forum.getId());
        if (autoTag != null && autoTag.getReviewTagName().equalsIgnoreCase(current.getSuggestionConfig().getGreenlitTag())) {
            DiscordUtils.findTagByName(forum, autoTag.getDefaultTagName()).ifPresent(tags::remove);
        }
    }

    private void save(GreenlitMessage message) {
        try {
            greenlit.save(message).join();
        } catch (CompletionException exception) {
            log.error("Failed to save greenlit suggestion {}; the autosave will retry", message.getMessageId(), exception);
        }
    }
}
