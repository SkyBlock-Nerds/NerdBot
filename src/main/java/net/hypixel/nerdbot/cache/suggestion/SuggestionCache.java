package net.hypixel.nerdbot.cache.suggestion;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.util.ArrayUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

@Log4j2
public class SuggestionCache extends TimerTask {

    private final Map<String, Suggestion> cache = new HashMap<>();
    @Getter
    private final Timer timer = new Timer();
    @Getter
    private boolean initialized = false;
    @Getter
    private boolean updating = false;

    public SuggestionCache() {
        this.timer.scheduleAtFixedRate(this, 0, Duration.ofMinutes(60).toMillis());
    }

    @Override
    public void run() {
        try {
            log.info("Started suggestion cache update.");

            this.updating = true;
            this.cache.forEach((key, suggestion) -> suggestion.setExpired());

            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();

            // Suggestions
            Optional<ForumChannel> suggestionChannel = ChannelCache.getForumChannelById(suggestionConfig.getForumChannelId());
            suggestionChannel.ifPresent(forumChannel -> this.loadSuggestions(forumChannel, Suggestion.ChannelType.NORMAL));

            // Alpha Suggestions
            ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds())
                .map(ChannelCache::getForumChannelById)
                .flatMap(Optional::stream)
                .filter(Objects::nonNull)
                .forEach(forumChannel -> this.loadSuggestions(forumChannel, Suggestion.ChannelType.ALPHA));

            // Project Suggestions
            ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds())
                .map(ChannelCache::getForumChannelById)
                .flatMap(Optional::stream)
                .filter(Objects::nonNull)
                .forEach(forumChannel -> this.loadSuggestions(forumChannel, Suggestion.ChannelType.PROJECT));

            log.info("Removing expired suggestions.");
            new ArrayList<>(cache.values())
                .stream()
                .filter(Suggestion::isExpired)
                .forEach(suggestion -> this.removeSuggestion(suggestion.getThreadName(), suggestion.getThreadId()));

            log.info("Finished caching suggestions.");
            this.initialized = true;
            this.updating = false;
        } catch (Exception exception) {
            log.error("Failed to update suggestion cache!", exception);
        }
    }

    private void loadSuggestions(ForumChannel forumChannel, Suggestion.ChannelType channelType) {
        try {
            Stream<ThreadChannel> unarchivedPosts = forumChannel.getThreadChannels().stream().sorted(
                (o1, o2) -> Long.compare(o2.getTimeCreated().toEpochSecond(), o1.getTimeCreated().toEpochSecond())
            );
            Stream<ThreadChannel> archivedPosts = forumChannel.retrieveArchivedPublicThreadChannels().stream();
            Stream.concat(unarchivedPosts, archivedPosts)
                .distinct()
                .forEach(threadChannel -> {
                    Suggestion suggestion = new Suggestion(threadChannel, channelType);
                    this.cache.put(threadChannel.getId(), suggestion);
                    log.debug("Added existing {} suggestion: '{}' (ID: {}) to the suggestion cache.", channelType.getName().toLowerCase(), threadChannel.getName(), threadChannel.getId());
                });
        } catch (Exception exception) {
            log.error("Failed to load suggestions from forum channel: " + forumChannel.getName(), exception);
        }
    }

    public void addSuggestion(ThreadChannel thread) {
        this.cache.put(thread.getId(), new Suggestion(thread));
        log.debug("Added new suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") to the suggestion cache.");
    }

    public Suggestion getSuggestion(String id) {
        return this.cache.get(id);
    }

    public List<Suggestion> getSuggestions() {
        return this.cache.values()
            .stream()
            .sorted((o1, o2) -> Long.compare( // Sort by most recent
                o2.getTimeCreated().toInstant().toEpochMilli(),
                o1.getTimeCreated().toInstant().toEpochMilli()
            ))
            .toList();
    }

    public void removeSuggestion(ThreadChannel threadChannel) {
        this.removeSuggestion(threadChannel.getName(), threadChannel.getId());
    }

    public void removeSuggestion(String threadName, String threadId) {
        this.cache.remove(threadId);
        log.debug("Removed suggestion '" + threadName + "' (ID: " + threadId + ") from the suggestion cache.");
    }

    public void updateSuggestion(ThreadChannel thread) {
        this.cache.put(thread.getId(), new Suggestion(thread));
        log.debug("Updated existing suggestion: '" + thread.getName() + "' (ID: " + thread.getId() + ") in the suggestion cache.");
    }
}
