package net.hypixel.nerdbot.bot.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.bot.config.objects.CustomForumTag;
import net.hypixel.nerdbot.bot.config.objects.ReactionChannel;

import java.util.List;

@Getter
@Setter
@ToString
public class ChannelConfig {

    /**
     * The {@link TextChannel} ID that the bot will be logging to
     */
    private String logChannelId = "";

    /**
     * Mojang profile verification requests will be logged to this {@link TextChannel} ID
     */
    private String verifyLogChannelId = "";

    /**
     * Public archive {@link Category} ID
     */
    private String publicArchiveCategoryId = "";

    /**
     * Nerd archive {@link Category} ID
     */
    private String nerdArchiveCategoryId = "";

    /**
     * Alpha archive {@link Category} ID
     */
    private String alphaArchiveCategoryId = "";

    /**
     * The {@link TextChannel} ID for an announcement channel, primarily used for things like fire sales or status updates
     */
    private String announcementChannelId = "";

    /**
     * The {@link TextChannel} ID for the bot-spam channel, used for miscellaneous bot output, mostly for debugging
     */
    private String botSpamChannelId = "";

    /**
     * The {@link TextChannel} ID for the poll channel
     */
    private String pollChannelId = "";

    /**
     * The {@link TextChannel} ID for the member voting channel
     */
    private String memberVotingChannelId = "";

    /**
     * The {@link TextChannel} ID for birthday notifications to be sent to
     */
    private String birthdayNotificationChannelId = "";

    /**
     * The {@link TextChannel} ID for the itemgen channel
     */
    private String[] genChannelIds = {};

    /**
     * Configuration for channels that will have reactions automatically added to all new messages
     */
    private List<ReactionChannel> reactionChannels = List.of();

    /**
     * The {@link TextChannel} IDs for the channels that the bot will ignore for activity tracking
     */
    private String[] blacklistedChannels = {};

    /**
     * The {@link CustomForumTag} list for custom forum tags
     * that may be used by specific users
     *
     * @see CustomForumTag#getOwnerId() for the owner of the tag
     */
    private List<CustomForumTag> customForumTags = List.of();

    /**
     * Automatically pin the first message in threads.
     * <br><br>
     * Default is true
     */
    private boolean autoPinFirstMessage = true;

    /**
     * The {@link ForumChannel} IDs for the channels ignored by the autopinning feature.
     */
    private String[] autoPinBlacklistedChannels = {};

}
