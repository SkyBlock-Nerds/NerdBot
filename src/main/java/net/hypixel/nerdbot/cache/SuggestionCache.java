package net.hypixel.nerdbot.cache;

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
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
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
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            Util.safeArrayStream(suggestionConfig.getSuggestionForumIds(), suggestionConfig.getAlphaSuggestionForumIds())
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

                    Message startMessage = suggestion.getThread().retrieveStartMessage().complete();

                    if (startMessage.getReactions().isEmpty()) {
                        log.debug("Suggestion '" + thread.getName() + "' (ID: " + thread.getId() + ") has no reactions.");
                        return;
                    }

                    startMessage.getReactions().stream()
                        .filter(messageReaction -> messageReaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                        .filter(messageReaction -> messageReaction.getEmoji().asCustom().getId().equalsIgnoreCase(suggestionConfig.getAgreeEmojiId())
                            || messageReaction.getEmoji().asCustom().getId().equalsIgnoreCase(suggestionConfig.getDisagreeEmojiId()))
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

    @Getter
    public static class Suggestion {

        private final ThreadChannel thread;
        private final Optional<Message> firstMessage;
        private final String parentId;
        private final String threadName;
        private final boolean alpha;
        private final int agrees;
        private final int disagrees;
        private final int neutrals;
        private final boolean greenlit;
        private final boolean deleted;
        private final long lastUpdated = System.currentTimeMillis();
        private boolean expired;
        private long lastBump = System.currentTimeMillis();

        public Suggestion(ThreadChannel thread) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            this.thread = thread;
            this.parentId = thread.getParentChannel().asForumChannel().getId();
            this.threadName = thread.getName();
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> forumTag.getId().equals(suggestionConfig.getGreenlitTag()) || forumTag.getId().equals(suggestionConfig.getReviewedTag()));
            this.expired = false;
            this.alpha = thread.getParentChannel().getName().toLowerCase().contains("alpha") || Util.safeArrayStream(suggestionConfig.getAlphaSuggestionForumIds()).anyMatch(this.parentId::equalsIgnoreCase);

            // Activity
            Message latestMessage = thread.getHistory().getMessageById(thread.getLatestMessageId());
            if (latestMessage != null) {
                long createdAt = latestMessage.getTimeCreated().toInstant().toEpochMilli();
                long currentTime = System.currentTimeMillis();
                long hoursAgo = TimeUnit.MILLISECONDS.toHours(currentTime - createdAt);
                boolean archive = false;
                boolean lock = false;

                if (hoursAgo >= suggestionConfig.getAutoArchiveThreshold()) {
                    archive = true;
                }

                if (hoursAgo >= suggestionConfig.getAutoLockThreshold()) {
                    lock = true;
                }

                if (archive || lock) {
                    thread.getManager().setArchived(archive).setLocked(lock).queue();
                }
            }

            // Message & Reactions
            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            if (history.isEmpty()) {
                this.firstMessage = Optional.empty();
                this.deleted = true;
                this.agrees = 0;
                this.disagrees = 0;
                this.neutrals = 0;
            } else {
                Message message = history.getRetrievedHistory().get(0);
                this.firstMessage = Optional.of(message);
                this.deleted = message.getIdLong() != thread.getIdLong();
                this.agrees = getReactionCount(message, suggestionConfig.getAgreeEmojiId());
                this.disagrees = getReactionCount(message, suggestionConfig.getDisagreeEmojiId());
                this.neutrals = getReactionCount(message, suggestionConfig.getNeutralEmojiId());
            }
        }

        public double getRatio() {
            if (this.getAgrees() == 0 && this.getDisagrees() == 0) {
                return 0;
            }
            return (double) this.getAgrees() / (this.getAgrees() + this.getDisagrees()) * 100.0;
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
