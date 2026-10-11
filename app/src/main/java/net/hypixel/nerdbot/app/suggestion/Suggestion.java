package net.hypixel.nerdbot.app.suggestion;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.marmalade.EnumUtils;
import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A snapshot of one suggestion thread and the votes on its first message. Building one has no side
 * effects; {@link SuggestionFactory} fetches the first message and {@link SuggestionCache} keeps
 * them up to date.
 */
@Getter
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
    private final ChannelType channelType;

    private Suggestion(ThreadChannel thread, ChannelType channelType, @Nullable Message firstMessage, NerdBotConfig config) {
        this.threadId = thread.getId();
        this.parentId = thread.getParentChannel().getId();
        this.threadName = thread.getName();
        this.ownerId = thread.getOwnerId();
        this.ownerIdLong = thread.getOwnerIdLong();
        this.guildId = thread.getGuild().getId();
        this.timeCreated = thread.getTimeCreated();
        this.jumpUrl = String.format("https://discord.com/channels/%s/%s", guildId, threadId);
        this.channelType = channelType;
        this.greenlit = channelType == ChannelType.NORMAL && DiscordUtils.hasTagByName(thread, config.getSuggestionConfig().getGreenlitTag());

        // A forum post's first message has the thread's id; anything else means the original was deleted
        this.deleted = firstMessage == null || firstMessage.getIdLong() != thread.getIdLong();
        EmojiConfig emojis = config.getEmojiConfig();
        this.agrees = deleted ? 0 : getReactionCount(firstMessage, emojis.getAgreeEmojiId());
        this.disagrees = deleted ? 0 : getReactionCount(firstMessage, emojis.getDisagreeEmojiId());
        this.neutrals = deleted ? 0 : getReactionCount(firstMessage, emojis.getNeutralEmojiId());
    }

    /**
     * @param firstMessage the thread's first message, or null when it couldn't be found
     */
    public static Suggestion of(ThreadChannel thread, ChannelType channelType, @Nullable Message firstMessage, NerdBotConfig config) {
        return new Suggestion(thread, channelType, firstMessage, config);
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

    /** The thread's tags, or none when it isn't in JDA's cache (archived threads usually aren't). */
    public List<ForumTag> getAppliedTags(JDA jda) {
        ThreadChannel threadChannel = jda.getThreadChannelById(this.getThreadId());
        return threadChannel == null ? List.of() : threadChannel.getAppliedTags();
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

    /** False when the thread isn't in JDA's cache, so archived threads usually aren't shown. */
    public boolean canSee(JDA jda, Member member) {
        ThreadChannel threadChannel = jda.getThreadChannelById(this.getThreadId());
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
