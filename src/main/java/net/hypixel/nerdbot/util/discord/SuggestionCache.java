package net.hypixel.nerdbot.util.discord;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.ReactionHistory;
import net.hypixel.nerdbot.bot.config.ChannelConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

@Log4j2
public class SuggestionCache extends TimerTask {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");
    private final Map<String, Suggestion> cache = new HashMap<>();

    @Getter
    private long lastUpdated;
    @Getter
    private final Timer timer = new Timer();

    public SuggestionCache() {
        this.timer.scheduleAtFixedRate(this, 0, Duration.ofMinutes(60).toMillis());
    }

    @Override
    public void run() {
        try {
            log.info("Started suggestion cache update.");
            this.cache.forEach((key, suggestion) -> suggestion.setExpired());
            ChannelConfig channelConfig = NerdBotApp.getBot().getConfig().getChannelConfig();
            Util.safeArrayStream(channelConfig.getSuggestionForumIds(), channelConfig.getAlphaSuggestionForumIds())
                .map(NerdBotApp.getBot().getJDA()::getForumChannelById)
                .filter(Objects::nonNull)
                .flatMap(forumChannel -> Stream.concat(
                    forumChannel.getThreadChannels().stream(), // Unarchived Posts
                    forumChannel.retrieveArchivedPublicThreadChannels().stream() // Archived Posts
                ))
                .distinct()
                .forEach(thread -> {
                    Suggestion suggestion = new Suggestion(thread);
                    this.cache.put(thread.getId(), suggestion);
                    log.debug("Added existing suggestion: '" + thread.getName() + "' (ID: " + thread.getId() + ") to the suggestion cache.");

                    if (suggestion.isDeleted()) {
                        return;
                    }

                    EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();
                    Message startMessage = suggestion.getThread().retrieveStartMessage().complete();

                    if (startMessage.getReactions().isEmpty()) {
                        log.debug("Suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") has no reactions.");
                        return;
                    }

                    startMessage.getReactions().stream()
                        .filter(messageReaction -> messageReaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                        .filter(messageReaction -> messageReaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getAgreeEmojiId())
                            || messageReaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getDisagreeEmojiId()))
                        .forEach(messageReaction -> {
                            messageReaction.retrieveUsers().complete().forEach(user -> {
                                DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                                DiscordUser discordUser = userRepository.findById(user.getId());

                                if (discordUser == null || discordUser.getLastActivity().getSuggestionReactionHistory().stream().anyMatch(history -> history.channelId().equals(suggestion.getParentId())
                                    && history.reactionName().equals(messageReaction.getEmoji().getName()))) {
                                    return;
                                }

                                discordUser.getLastActivity().getSuggestionReactionHistory().add(new ReactionHistory(thread.getId(), messageReaction.getEmoji().getName(), thread.getTimeCreated().toEpochSecond(), -1));
                                log.debug("Added reaction history for user '" + user.getId() + "' on suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ")");
                            });
                        });
                });

            log.info("Removing expired suggestions.");
            new ArrayList<>(cache.values())
                .stream()
                .filter(Suggestion::isExpired)
                .forEach(suggestion -> this.removeSuggestion(suggestion.getThread()));

            log.info("Finished caching suggestions.");
        } catch (Exception ex) {
            ex.printStackTrace();
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
                o2.getThread().getTimeCreated().toInstant().toEpochMilli(),
                o1.getThread().getTimeCreated().toInstant().toEpochMilli()
            ))
            .toList();
    }

    public void removeSuggestion(ThreadChannel thread) {
        this.cache.remove(thread.getId());
        log.debug("Removed suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") from the suggestion cache.");
    }

    public static class Suggestion {

        @Getter
        private final ThreadChannel thread;
        @Getter
        private final String parentId;
        @Getter
        private final String threadName;
        @Getter
        private final boolean alpha;
        @Getter
        private final int agrees;
        @Getter
        private final int disagrees;
        @Getter
        private final boolean greenlit;
        @Getter
        private final boolean deleted;
        @Getter
        private final long lastUpdated = System.currentTimeMillis();
        @Getter
        private boolean expired;

        public Suggestion(ThreadChannel thread) {
            this.thread = thread;
            this.parentId = thread.getParentChannel().asForumChannel().getId();
            this.threadName = thread.getName();
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> ForumChannelCurator.GREENLIT_TAGS.contains(forumTag.getName()));
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
            return message.getReactions()
                .stream()
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
