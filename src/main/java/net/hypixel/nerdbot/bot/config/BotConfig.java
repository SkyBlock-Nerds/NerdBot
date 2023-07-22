package net.hypixel.nerdbot.bot.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;

@Getter
@Setter
@ToString
public class BotConfig {

    /**
     * {@link Emoji Emojis} that the bot will use to react to suggestions
     */
    private EmojiConfig emojiConfig = new EmojiConfig();

    /**
     * {@link ForumTag Forum tags} for the bot to use, such as the greenlit tag to set suggestions as greenlit
     */
    private TagConfig tagConfig = new TagConfig();

    /**
     * Configuration for channels that the bot will be using
     */
    private ChannelConfig channelConfig = new ChannelConfig();

    /**
     * Configuration for roles that the bot will be using
     */
    private RoleConfig roleConfig = new RoleConfig();

    /**
     * The {@link Guild} ID that the bot will be running in
     */
    private String guildId = "";

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
     * The number of hours before re-caching all Minecraft Usernames
     * Default value is 12 hours
     */
    private int mojangUsernameCache = 12;

    /**
     * How long someone must be in the same voice channel for it to count towards activity, in seconds.
     */
    private long voiceThreshold = 60;

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
     * The {@link Activity.ActivityType} that the bot will display on its profile
     * Default is PLAYING
     */
    private Activity.ActivityType activityType = Activity.ActivityType.PLAYING;

    /**
     * The message being displayed as the bots {@link Activity} on its profile
     */
    private String activity = "";

    /**
     * The amount of days that a user must be inactive for to show up in the inactive user list
     */
    private int inactivityDays = 7;
}
