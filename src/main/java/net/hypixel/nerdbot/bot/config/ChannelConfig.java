package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.channel.ReactionChannel;

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
     * The {@link TextChannel} IDs for the suggestion forums
     */
    private String[] suggestionForumIds = {};

    /**
     * The {@link TextChannel} IDs for the alpha suggestion forums
     */
    private String[] alphaSuggestionForumIds = {};

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
     * Whether automatic pinning of first messages in threads should be enabled. This is disabled by default.
     */
    private boolean automaticallyPinnedThread = false;
}
