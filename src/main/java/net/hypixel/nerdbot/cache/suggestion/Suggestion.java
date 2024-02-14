package net.hypixel.nerdbot.cache.suggestion;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.util.Util;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Getter
@Log4j2
public class Suggestion {

    private final ThreadChannel thread;
    private final Optional<Message> firstMessage;
    private final String parentId;
    private final String threadName;
    private final int agrees;
    private final int disagrees;
    private final int neutrals;
    private final boolean greenlit;
    private final boolean deleted;
    private final long lastUpdated = System.currentTimeMillis();
    private final long lastBump = System.currentTimeMillis();
    private final Type type;
    private boolean expired;

    public Suggestion(ThreadChannel thread) {
        this(thread, null);
    }

    public Suggestion(ThreadChannel thread, Type type) {
        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        this.thread = thread;
        this.parentId = thread.getParentChannel().asForumChannel().getId();
        this.threadName = thread.getName();
        this.greenlit = type == Type.NORMAL && Util.hasTagByName(thread, botConfig.getSuggestionConfig().getGreenlitTag());
        this.type = type == null ? Util.getSuggestionType(thread) : type;
        this.expired = false;

        // Activity
        Message latestMessage = thread.getHistory().getMessageById(thread.getLatestMessageId());
        if (latestMessage != null) {
            long createdAt = latestMessage.getTimeCreated().toInstant().toEpochMilli();
            long currentTime = System.currentTimeMillis();
            long hoursAgo = TimeUnit.MILLISECONDS.toHours(currentTime - createdAt);
            ThreadChannelManager threadManager = thread.getManager();
            boolean changed = false;
            long autoArchiveThreshold = type == Type.NORMAL ? botConfig.getSuggestionConfig().getAutoArchiveThreshold() : botConfig.getAlphaProjectConfig().getAutoArchiveThreshold();
            long autoLockThreshold = type == Type.NORMAL ? botConfig.getSuggestionConfig().getAutoLockThreshold() : botConfig.getAlphaProjectConfig().getAutoLockThreshold();

            if (hoursAgo >= autoArchiveThreshold) {
                log.debug("Auto-archiving suggestion '{}' (ID: {}) due to inactivity. (Hours: {}, Auto Archive Threshold: {})", thread.getName(), thread.getId(), hoursAgo, autoArchiveThreshold);
                threadManager = threadManager.setArchived(true);
                changed = true;
            }

            if (hoursAgo >= autoLockThreshold) {
                log.debug("Auto-locking suggestion '{}' (ID: {}) due to inactivity. (Hours: {}, Auto Archive Threshold: {})", thread.getName(), thread.getId(), hoursAgo, autoLockThreshold);
                threadManager = threadManager.setLocked(true);
                changed = true;
            }

            if (changed) {
                threadManager.queue();
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
            this.agrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.neutrals = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getNeutralEmojiId());
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

    public double getRatio() {
        if (this.getAgrees() == 0 && this.getDisagrees() == 0) {
            return 0;
        }

        return (double) this.getAgrees() / (this.getAgrees() + this.getDisagrees()) * 100.0;
    }

    public boolean notDeleted() {
        return !this.isDeleted();
    }

    void setExpired() {
        this.expired = true;
    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        UNKNOWN("Unknown"),
        NORMAL("Normal"),
        ALPHA("Alpha"),
        PROJECT("Project");

        public static final Type[] VALUES = new Type[] { NORMAL, ALPHA, PROJECT };

        private final String name;

        public static Type getType(String name) {
            for (Type type : VALUES) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }

            return UNKNOWN;
        }

    }
}