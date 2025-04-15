package net.hypixel.nerdbot.cache.suggestion;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.util.Util;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Getter
@Log4j2
public class Suggestion {

    private final String threadId;
    private final String parentId;
    private final String threadName;
    private final String ownerId;
    private final long ownerIdLong;
    private final String guildId;
    private final OffsetDateTime timeCreated;
    private final String jumpUrl;
    private final int agrees;
    private final int disagrees;
    private final int neutrals;
    private final boolean greenlit;
    private final boolean deleted;
    private final long lastUpdated = System.currentTimeMillis();
    private final long lastBump = System.currentTimeMillis();
    private final ChannelType channelType;
    private boolean expired;

    public Suggestion(ThreadChannel thread) {
        this(thread, null);
    }

    public Suggestion(ThreadChannel thread, ChannelType channelType) {
        BotConfig botConfig = NerdBotApp.getBot().getConfig();
        this.threadId = thread.getId();
        this.parentId = thread.getParentChannel().asForumChannel().getId();
        this.threadName = thread.getName();
        this.ownerId = thread.getOwnerId();
        this.ownerIdLong = thread.getOwnerIdLong();
        this.guildId = thread.getGuild().getId();
        this.timeCreated = thread.getTimeCreated();
        this.jumpUrl = String.format("https://discord.com/channels/%s/%s", this.getGuildId(), this.getThreadId());
        this.greenlit = channelType == ChannelType.NORMAL && Util.hasTagByName(thread, botConfig.getSuggestionConfig().getGreenlitTag());
        this.channelType = channelType == null ? Util.getThreadSuggestionType(thread) : channelType;
        this.expired = false;

        // Activity
        Message latestMessage = thread.getHistory().getMessageById(thread.getLatestMessageId());
        if (latestMessage != null) {
            long createdAt = latestMessage.getTimeCreated().toInstant().toEpochMilli();
            long currentTime = System.currentTimeMillis();
            long hoursAgo = TimeUnit.MILLISECONDS.toHours(currentTime - createdAt);
            ThreadChannelManager threadManager = thread.getManager();
            boolean changed = false;
            long autoArchiveThreshold = channelType == ChannelType.NORMAL ? botConfig.getSuggestionConfig().getAutoArchiveThreshold() : botConfig.getAlphaProjectConfig().getAutoArchiveThreshold();
            long autoLockThreshold = channelType == ChannelType.NORMAL ? botConfig.getSuggestionConfig().getAutoLockThreshold() : botConfig.getAlphaProjectConfig().getAutoLockThreshold();

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
        Optional<Message> firstMessage = this.getFirstMessage();
        if (firstMessage.isEmpty()) {
            this.deleted = true;
            this.agrees = 0;
            this.disagrees = 0;
            this.neutrals = 0;
        } else {
            Message message = firstMessage.get();
            this.deleted = message.getIdLong() != thread.getIdLong();
            this.agrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.neutrals = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getNeutralEmojiId());
        }
    }

    public List<ForumTag> getAppliedTags() {
        ThreadChannel threadChannel = NerdBotApp.getBot().getJDA().getThreadChannelById(this.getThreadId());
        return threadChannel == null ? List.of() : threadChannel.getAppliedTags();
    }

    public Optional<Message> getFirstMessage() {
        return Util.getFirstMessage(this.getThreadId());
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

    public boolean canSee(Member member) {
        ThreadChannel threadChannel = NerdBotApp.getBot().getJDA().getThreadChannelById(this.getThreadId());
        return threadChannel != null && member.hasPermission(threadChannel, Permission.VIEW_CHANNEL);
    }

    @Getter
    @RequiredArgsConstructor
    public enum ChannelType {

        UNKNOWN("Unknown"),
        NORMAL("Normal"),
        ALPHA("Alpha"),
        PROJECT("Project");

        public static final ChannelType[] VALUES = new ChannelType[]{NORMAL, ALPHA, PROJECT};

        private final String name;

        public static ChannelType getType(String name) {
            return Arrays.stream(VALUES)
                .filter(channelType -> channelType.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(UNKNOWN);
        }
    }
}