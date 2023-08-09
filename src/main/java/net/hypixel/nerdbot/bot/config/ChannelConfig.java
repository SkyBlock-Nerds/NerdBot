package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
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
}
