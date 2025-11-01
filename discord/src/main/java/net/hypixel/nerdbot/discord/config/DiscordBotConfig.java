package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.channel.ModMailConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Base configuration for Discord bots using the framework.
 * Contains framework-level settings that are common across different Discord bot implementations.
 */
@Getter
@Setter
@ToString
public class DiscordBotConfig {

    /**
     * The Guild ID that the bot will be running in
     */
    private String guildId = "";

    /**
     * A list of owner IDs for the bot
     */
    private List<String> ownerIds = new ArrayList<>();

    /**
     * Configuration for anything related to the Mod Mail feature
     */
    private ModMailConfig modMailConfig = new ModMailConfig();

    /**
     * Configuration for the suggestions channel
     */
    private SuggestionConfig suggestionConfig = new SuggestionConfig();

    /**
     * Configuration for alpha/project suggestion channels
     */
    private AlphaProjectConfig alphaProjectConfig = new AlphaProjectConfig();

    /**
     * Configuration for channels that the bot will be using
     */
    private ChannelConfig channelConfig = new ChannelConfig();

    /**
     * Configuration for roles that the bot will be using
     */
    private RoleConfig roleConfig = new RoleConfig();

    /**
     * Configuration for emoji IDs
     */
    private EmojiConfig emojiConfig = new EmojiConfig();

    /**
     * Configuration for badges
     */
    private BadgeConfig badgeConfig = new BadgeConfig();

    /**
     * Configuration for fun and miscellaneous things
     */
    private FunConfig funConfig = new FunConfig();

    /**
     * Configuration for generator commands
     */
    private GeneratorConfig generatorConfig = new GeneratorConfig();

    /**
     * The activity type that the bot will display on its profile
     * Default is WATCHING
     */
    private ActivityType activityType = ActivityType.WATCHING;

    /**
     * The message being displayed as the bots activity on its profile
     * Default is "with a default message!"
     */
    private String activity = "with a default message!";

    public enum ActivityType {
        PLAYING,
        STREAMING,
        LISTENING,
        WATCHING,
        COMPETING,
        CUSTOM
    }
}