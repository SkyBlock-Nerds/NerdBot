package net.hypixel.nerdbot.util.discord;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

@Log4j2
public class SuggestionCache extends TimerTask {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");
    private Map<String, Suggestion> cache = new HashMap<>();
    @Getter private boolean loaded;
    @Getter private long lastUpdated;
    @Getter private final Timer timer = new Timer();

    public SuggestionCache() {
        this.timer.scheduleAtFixedRate(this, 0, Duration.ofMinutes(60).toMillis());
    }

    @Override
    public void run() {
        try {
            log.info("Started suggestion cache update.");
            this.loaded = false;
            this.cache.forEach((key, suggestion) -> suggestion.setExpired());
            ChannelConfig channelConfig = NerdBotApp.getBot().getConfig().getChannelConfig();
            Util.safeArrayStream(channelConfig.getSuggestionForumIds(), channelConfig.getAlphaSuggestionForumIds())
                .map(NerdBotApp.getBot().getJDA()::getForumChannelById)
                .filter(Objects::nonNull)
                .flatMap(forumChannel -> Stream.concat(forumChannel.getThreadChannels().stream(), forumChannel.retrieveArchivedPublicThreadChannels().stream()))
                .distinct()
                .forEach(thread -> {
                    this.cache.put(thread.getId(), new Suggestion(thread));
                    User user = NerdBotApp.getBot().getJDA().getUserById(thread.getOwnerIdLong());
                    //log.info("Added existing suggestion: '" + thread.getName() + "' (ID: " + thread.getId() + ") (User: " + (user != null ? user.getEffectiveName() + "/" : "") + thread.getOwnerId() + ") to the suggestion cache.");
                });

            log.info("Removing expired suggestions.");
            new ArrayList<>(cache.values())
                .stream()
                .filter(Suggestion::isExpired)
                .forEach(suggestion -> this.removeSuggestion(suggestion.getThread()));

            this.loaded = true;
            log.info("Finished caching suggestions.");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void addSuggestion(ThreadChannel thread) {
        this.cache.put(thread.getId(), new Suggestion(thread));
        //log.info("Added new suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") to the suggestion cache.");
    }

    public Suggestion getSuggestion(String id) {
        return this.cache.get(id);
    }

    public List<Suggestion> getSuggestions() {
        return this.cache.values()
            .stream()
            .sorted((o1, o2) -> Long.compare( // Sort by most recent
                o2.getThread().getTimeCreated().toInstant().toEpochMilli(),
                o1.getThread().getTimeCreated().toInstant().toEpochMilli()
            ))
            .toList();
    }

    public void removeSuggestion(ThreadChannel thread) {
        this.cache.remove(thread.getId());
        //log.info("Removed suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") from the suggestion cache.");
    }

    public static class Suggestion {

        @Getter private final ThreadChannel thread;
        @Getter private final String parentId;
        @Getter private final String threadName;
        @Getter private final boolean alpha;
        @Getter private final int agrees;
        @Getter private final int disagrees;
        @Getter private final boolean greenlit;
        @Getter private final boolean deleted;
        @Getter private final long lastUpdated = System.currentTimeMillis();
        @Getter private boolean expired;

        public Suggestion(ThreadChannel thread) {
            this.thread = thread;
            this.parentId = thread.getParentChannel().asForumChannel().getId();
            this.threadName = thread.getName();
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> GREENLIT_TAGS.contains(forumTag.getName().toLowerCase()));
            this.expired = false;
            this.alpha = thread.getName().toLowerCase().contains("alpha") || Util.safeArrayStream(NerdBotApp.getBot().getConfig().getChannelConfig().getAlphaSuggestionForumIds()).anyMatch(this.parentId::equalsIgnoreCase);

            // Message & Reactions
            MessageHistory history = thread.getHistoryFromBeginning(1).complete();

            if (history.isEmpty()) {
                this.deleted = true;
                this.agrees = 0;
                this.disagrees = 0;
            } else {
                Message message = history.getRetrievedHistory().get(0);
                this.deleted = message.getIdLong() != thread.getIdLong();
                this.agrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
                this.disagrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            }
        }

        public static int getReactionCount(Message message, String emojiId) {
            return message.getReactions().stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .filter(reaction -> reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiId))
                .mapToInt(MessageReaction::getCount)
                .findFirst()
                .orElse(0);
        }

        public boolean notDeleted() {
            return !this.isDeleted();
        }

        private void setExpired() {
            this.expired = true;
        }
    }

}
