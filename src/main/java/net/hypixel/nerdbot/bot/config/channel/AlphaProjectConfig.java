package net.hypixel.nerdbot.bot.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.util.Util;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Log4j2
@ToString
public class AlphaProjectConfig {

    // Channels

    /**
     * The {@link TextChannel alpha channel} IDs
     */
    private String[] alphaForumIds = {};

    /**
     * The {@link ForumChannel project channel} IDs
     */
    private String[] projectForumIds = {};

    // Forum Tags
    /**
     * Automatically create the above tags on new forum channel creation.
     */
    private boolean autoCreateTags = true;

    // Features

    /**
     * Automatically pin the first message in threads.
     * <br><br>
     * Default is false
     */
    private boolean autoPinFirstMessage = false;

    /**
     * The amount of hours with no activity to archive a thread.
     * <br><br>
     * Default is 7 days
     * <br>
     * Set to 0 to disable
     */
    private int autoArchiveThreshold = 24 * 7;

    /**
     * The amount of hours with no activity to lock a thread.
     * <br><br>
     * Default is disabled
     * <br>
     * Set to -1 to disable
     */
    private int autoLockThreshold = -1;

    // Helper Methods

    public void updateForumIds(BotConfig botConfig, boolean updateAlpha, boolean updateProject) {
        List<ForumChannel> forumChannels = new ArrayList<>(NerdBotApp.getBot().getJDA().getForumChannels());
        forumChannels.removeIf(forumChannel -> forumChannel.getId().equals(botConfig.getSuggestionConfig().getForumChannelId())); // Remove Suggestion Channel

        if (updateAlpha) {
            // Update Alpha Forum IDs (Alpha Takes Priority)
            log.info("Updating Alpha Forum IDs");
            this.alphaForumIds = forumChannels.stream()
                .filter(forumChannel -> Util.getForumSuggestionType(forumChannel) == Suggestion.ChannelType.ALPHA)
                .map(ISnowflake::getId)
                .toList()
                .toArray(new String[]{});
        }

        if (updateProject) {
            // Update Project Forum IDs
            log.info("Updating Project Forum IDs");
            this.projectForumIds = forumChannels.stream()
                .filter(forumChannel -> Util.getForumSuggestionType(forumChannel) == Suggestion.ChannelType.PROJECT)
                .map(ISnowflake::getId)
                .toList()
                .toArray(new String[]{});
        }

        NerdBotApp.getBot().writeConfig(botConfig);
    }
}
