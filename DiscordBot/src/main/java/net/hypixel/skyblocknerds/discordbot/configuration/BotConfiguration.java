package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class BotConfiguration implements IConfiguration {

    /**
     * An array of {@link String} owner IDs for the bot
     */
    private List<String> ownerIds = new ArrayList<>();

    /**
     * The {@link Activity.ActivityType} that the bot will display
     * <p>
     * Default is {@link Activity.ActivityType#WATCHING}
     */
    private Activity.ActivityType activityType = Activity.ActivityType.WATCHING;

    /**
     * The message that the bot will display with the {@link Activity.ActivityType}
     * <p>
     * Default is "with a default message"
     */
    private String activityMessage = "with a default message";

    /**
     * The package containing the commands for the bot
     * <p>
     * Default is "net.hypixel.skyblocknerds.discordbot.command"
     */
    private String commandPackage = "net.hypixel.skyblocknerds.discordbot.command";
}
