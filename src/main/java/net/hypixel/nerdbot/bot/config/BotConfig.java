package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;

@Getter
@Setter
@ToString
public class BotConfig {

    /**
     * The {@link Guild} ID that the bot will be running in
     */
    private String guildId;

    /**
     * The {@link TextChannel} ID that the bot will be logging to
     */
    private String logChannel;

    /**
     * The {@link Role} ID of the Bot Manager role
     */
    private String botManagerRoleId;

    /**
     * The {@link TextChannel} that the bot will be generating items in
     */

    private String itemGenChannel;

    /**
     * The {@link TextChannel} ID for the suggestion forum
     */
    private String suggestionForumId;

    /**
     * The minimum threshold of {@link MessageReaction reactions} needed for a suggestion to be considered greenlit
     * Default value is 15 {@link MessageReaction reactions}
     */
    private int minimumThreshold = 15;

    /**
     * The limit of {@link Message messages} that the bot will curate in one go
     * Default value is 100 {@link Message messages}
     */
    private int messageLimit = 100;

    /**
     * The percentage of positive {@link MessageReaction reactions} needed for a suggestion to be considered greenlit
     * Default value is 75%
     */
    private double percentage = 75;

    /**
     * The interval between each curate cycle in milliseconds
     * Default value is 43200000 (12 hours)
     */
    private long interval = 43_200_000;

    /**
     * {@link Emoji Emojis} that the bot will use to react to suggestions
     */
    private EmojiConfig emojiConfig;

    /**
     * {@link ForumTag Forum tags} for the bot to use, such as the greenlit tag to set suggestions as greenlit
     */
    private TagConfig tagConfig;

    /**
     * The {@link Activity.ActivityType} that the bot will display on its profile
     * Default is PLAYING
     */
    private Activity.ActivityType activityType = Activity.ActivityType.PLAYING;

    /**
     * The message being displayed as the bots {@link Activity} on its profile
     */
    private String activity;

    /**
     * Configuration for anything related to the Mod Mail feature
     */
    private ModMailConfig modMailConfig;
}
