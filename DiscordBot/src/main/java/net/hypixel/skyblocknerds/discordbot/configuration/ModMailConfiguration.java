package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class ModMailConfiguration implements IConfiguration {

    /**
     * The {@link Channel} ID that Mod Mail will be sent to and received from
     */
    private String modMailChannelId;

    /**
     * The {@link Webhook} ID that will be used to send Mod Mail messages to the {@link #modMailChannelId}
     */
    private String incomingModMailWebhookId;

    /**
     * The {@link Role} ID that will be used to ping the Mod Mail team when a new Mod Mail message is received
     */
    private String notificationRoleId;

    /**
     * The amount of time in seconds between pings to the Mod Mail team when a new Mod Mail message is received
     * <p>
     * Default is 60 seconds
     */
    private int timeBetweenPingsSeconds = 60;
}
