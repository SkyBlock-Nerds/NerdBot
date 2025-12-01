package net.hypixel.nerdbot.discord.config.channel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

@Getter
@Setter
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

}