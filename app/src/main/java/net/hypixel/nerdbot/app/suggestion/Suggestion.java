package net.hypixel.nerdbot.app.suggestion;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.hypixel.nerdbot.marmalade.EnumUtils;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
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
        NerdBotConfig botConfig = SkyBlockNerdsBot.config();
        this.threadId = thread.getId();
        this.parentId = thread.getParentChannel().asForumChannel().getId();
        this.threadName = thread.getName();
        this.ownerId = thread.getOwnerId();
        this.ownerIdLong = thread.getOwnerIdLong();
        this.guildId = thread.getGuild().getId();
        this.timeCreated = thread.getTimeCreated();
        this.jumpUrl = String.format("https://discord.com/channels/%s/%s", this.getGuildId(), this.getThreadId());
        this.channelType = channelType == null ? SuggestionTypeResolver.getThreadSuggestionType(thread) : channelType;
        this.greenlit = this.channelType == ChannelType.NORMAL && DiscordUtils.hasTagByName(thread, botConfig.getSuggestionConfig().getGreenlitTag());
        this.expired = false;

        applyAutoArchiveAndLock(thread, botConfig);

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
            this.agrees = getReactionCount(message, DiscordBotEnvironment.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = getReactionCount(message, DiscordBotEnvironment.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.neutrals = getReactionCount(message, DiscordBotEnvironment.getBot().getConfig().getEmojiConfig().getNeutralEmojiId());
        }
    }

    private void applyAutoArchiveAndLock(ThreadChannel thread, NerdBotConfig botConfig) {
        if (channelType == ChannelType.UNKNOWN) {
            return;
        }

        boolean normal = channelType == ChannelType.NORMAL;
        int archiveAfterHours = normal ? botConfig.getSuggestionConfig().getAutoArchiveThreshold() : botConfig.getAlphaProjectConfig().getAutoArchiveThreshold();
        int lockAfterHours = normal ? botConfig.getSuggestionConfig().getAutoLockThreshold() : botConfig.getAlphaProjectConfig().getAutoLockThreshold();
        long lastActivity = thread.getLatestMessageIdLong() != 0
            ? TimeUtil.getTimeCreated(thread.getLatestMessageIdLong()).toInstant().toEpochMilli()
            : thread.getTimeCreated().toInstant().toEpochMilli();
        long hoursInactive = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - lastActivity);

        AutoArchiveDecision decision = decideAutoArchive(hoursInactive, archiveAfterHours, lockAfterHours, thread.isArchived(), thread.isLocked());
        if (!decision.archive() && !decision.lock()) {
            return;
        }

        ThreadChannelManager manager = thread.getManager();
        if (decision.archive()) {
            manager = manager.setArchived(true);
        }
        if (decision.lock()) {
            manager = manager.setLocked(true);
        }

        log.info("Suggestion '{}' (ID: {}) has had no activity for {} hours (archive: {}, lock: {})",
            thread.getName(), thread.getId(), hoursInactive, decision.archive(), decision.lock());
        manager.queue(null, failure -> log.warn("Failed to auto-archive/lock suggestion '{}' (ID: {})", thread.getName(), thread.getId(), failure));
    }

    record AutoArchiveDecision(boolean archive, boolean lock) {
    }

    /**
     * A threshold of zero or less turns that action off. Threads that are already archived are left
     * alone: Discord won't edit an archived thread (including locking it) without unarchiving it first.
     */
    static AutoArchiveDecision decideAutoArchive(long hoursInactive, int archiveAfterHours, int lockAfterHours, boolean archived, boolean locked) {
        if (archived) {
            return new AutoArchiveDecision(false, false);
        }

        boolean archive = archiveAfterHours > 0 && hoursInactive >= archiveAfterHours;
        boolean lock = !locked && lockAfterHours > 0 && hoursInactive >= lockAfterHours;
        return new AutoArchiveDecision(archive, lock);
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

    public List<ForumTag> getAppliedTags() {
        ThreadChannel threadChannel = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(this.getThreadId());
        return threadChannel == null ? List.of() : threadChannel.getAppliedTags();
    }

    public Optional<Message> getFirstMessage() {
        return DiscordUtils.getFirstMessage(this.getThreadId());
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
        ThreadChannel threadChannel = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(this.getThreadId());
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
            ChannelType result = EnumUtils.findValue(VALUES, name);
            return result != null ? result : UNKNOWN;
        }
    }
}