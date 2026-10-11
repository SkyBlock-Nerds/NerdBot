package net.hypixel.nerdbot.app.suggestion;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Every suggestion thread in the configured suggestion, alpha and project forums.
 *
 * <p>A full refresh runs hourly on the shared scheduler, and also archives and locks threads that
 * have gone quiet. Between refreshes, a vote or new post calls {@link #requestUpdate}; updates for
 * the same thread within {@link #UPDATE_DELAY} are merged into one fetch, so a burst of votes costs
 * one request and nothing blocks the thread that reported the vote.
 */
@Slf4j
@Singleton
public class SuggestionCache {

    static final Duration REFRESH_INTERVAL = Duration.ofHours(1);
    public static final Duration UPDATE_DELAY = Duration.ofSeconds(10);

    private record Forum(ForumChannel channel, Suggestion.ChannelType type) {
    }

    private final ScheduledExecutorService scheduler;
    private final SuggestionFactory factory;
    private final Provider<NerdBotConfig> config;
    private final JDA jda;
    private final Clock clock;
    private final Map<String, Suggestion> suggestions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> pendingUpdates = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    @Inject
    public SuggestionCache(ScheduledExecutorService scheduler, SuggestionFactory factory, Provider<NerdBotConfig> config, JDA jda, Clock clock) {
        this.scheduler = scheduler;
        this.factory = factory;
        this.config = config;
        this.jda = jda;
        this.clock = clock;
    }

    /** Runs the first refresh now and then hourly; the next one is scheduled when the last finishes, so they never overlap. */
    public void start() {
        scheduleRefresh(Duration.ZERO);
    }

    public Optional<Suggestion> find(String threadId) {
        return Optional.ofNullable(suggestions.get(threadId));
    }

    /** Every cached suggestion, newest first. */
    public List<Suggestion> all() {
        return suggestions.values().stream()
            .sorted(Comparator.comparing(Suggestion::getTimeCreated).reversed())
            .toList();
    }

    /** False until the first refresh has finished, so results may be incomplete. */
    public boolean isInitialized() {
        return initialized;
    }

    /** Fetches the thread again shortly, merging repeated requests for the same thread. */
    public void requestUpdate(ThreadChannel thread) {
        try {
            pendingUpdates.computeIfAbsent(thread.getId(), threadId ->
                scheduler.schedule(() -> update(thread), UPDATE_DELAY.toMillis(), TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("Not updating suggestion {} as the bot is shutting down", thread.getId());
        }
    }

    public void remove(String threadId) {
        ScheduledFuture<?> pending = pendingUpdates.remove(threadId);
        if (pending != null) {
            pending.cancel(false);
        }
        suggestions.remove(threadId);
        publishCount();
    }

    private void update(ThreadChannel thread) {
        pendingUpdates.remove(thread.getId());
        factory.build(thread, null)
            .thenAccept(suggestion -> {
                suggestions.put(thread.getId(), suggestion);
                publishCount();
            })
            .exceptionally(error -> {
                log.warn("Couldn't update suggestion '{}' (ID: {})", thread.getName(), thread.getId(), error);
                return null;
            });
    }

    private void scheduleRefresh(Duration delay) {
        try {
            scheduler.schedule(() -> refresh().whenComplete((ignored, error) -> {
                if (error != null) {
                    log.error("Suggestion cache refresh failed", error);
                }
                scheduleRefresh(REFRESH_INTERVAL);
            }), delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("Not scheduling another suggestion refresh as the bot is shutting down");
        }
    }

    /**
     * Reloads every forum. A forum that fails to load keeps what was cached for it, and a thread
     * whose fetch fails keeps its last snapshot, so a Discord hiccup doesn't empty the cache.
     */
    CompletableFuture<Void> refresh() {
        log.info("Started suggestion cache update");
        NerdBotConfig current = config.get();

        List<CompletableFuture<Void>> loads = forums(current).stream()
            .map(forum -> loadForum(forum, current).exceptionally(error -> {
                log.error("Failed to load suggestions from forum {} (ID: {})", forum.channel().getName(), forum.channel().getId(), error);
                return null;
            }))
            .toList();

        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenRun(() -> {
            initialized = true;
            publishCount();
            log.info("Finished caching {} suggestions", suggestions.size());
        });
    }

    private CompletableFuture<Void> loadForum(Forum forum, NerdBotConfig current) {
        List<ThreadChannel> active = forum.channel().getThreadChannels();

        return forum.channel().retrieveArchivedPublicThreadChannels().takeAsync(Integer.MAX_VALUE).thenCompose(archived -> {
            Map<String, ThreadChannel> threads = new LinkedHashMap<>();
            active.forEach(thread -> threads.put(thread.getId(), thread));
            archived.forEach(thread -> threads.putIfAbsent(thread.getId(), thread));

            List<CompletableFuture<Void>> builds = new ArrayList<>();
            for (ThreadChannel thread : threads.values()) {
                SuggestionAutoArchiver.apply(thread, forum.type(), current, clock.millis());
                builds.add(factory.build(thread, forum.type())
                    .thenAccept(suggestion -> suggestions.put(thread.getId(), suggestion))
                    .exceptionally(error -> {
                        log.warn("Couldn't refresh suggestion '{}' (ID: {}), keeping the last copy", thread.getName(), thread.getId(), error);
                        return null;
                    }));
            }

            return CompletableFuture.allOf(builds.toArray(CompletableFuture[]::new)).thenRun(() -> {
                Set<String> stillThere = new HashSet<>(threads.keySet());
                suggestions.values().removeIf(suggestion -> suggestion.getParentId().equals(forum.channel().getId())
                    && !stillThere.contains(suggestion.getThreadId()));
            });
        });
    }

    private List<Forum> forums(NerdBotConfig current) {
        List<Forum> forums = new ArrayList<>();
        forumById(current.getSuggestionConfig().getForumChannelId())
            .ifPresent(channel -> forums.add(new Forum(channel, Suggestion.ChannelType.NORMAL)));
        ArrayUtils.safeArrayStream(current.getAlphaProjectConfig().getAlphaForumIds())
            .map(this::forumById)
            .flatMap(Optional::stream)
            .forEach(channel -> forums.add(new Forum(channel, Suggestion.ChannelType.ALPHA)));
        ArrayUtils.safeArrayStream(current.getAlphaProjectConfig().getProjectForumIds())
            .map(this::forumById)
            .flatMap(Optional::stream)
            .forEach(channel -> forums.add(new Forum(channel, Suggestion.ChannelType.PROJECT)));
        return forums;
    }

    // An unset or malformed id in the config just means that forum isn't loaded
    private Optional<ForumChannel> forumById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jda.getForumChannelById(id));
        } catch (NumberFormatException malformed) {
            log.warn("Forum id '{}' in the config is not a valid id", id);
            return Optional.empty();
        }
    }

    private void publishCount() {
        PrometheusMetrics.TOTAL_SUGGESTIONS_AMOUNT.set(suggestions.size());
    }
}
