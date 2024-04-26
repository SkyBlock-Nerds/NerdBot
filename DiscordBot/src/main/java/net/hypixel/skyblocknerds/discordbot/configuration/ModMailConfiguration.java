package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class ModMailConfiguration implements IConfiguration {

    private String modMailChannelId;

    private String incomingModMailWebhookId;

    private String notificationRoleId;

    private int timeBetweenPingsSeconds = 60;
}
