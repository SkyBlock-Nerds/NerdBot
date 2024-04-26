package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.skyblocknerds.api.configuration.IConfiguration;

@Getter
@Setter
public class MojangConfiguration implements IConfiguration {

    /**
     * The number of hours before usernames are updated
     * from the Mojang API. Default is 12 hours.
     */
    private int usernameCacheHours = 12;

    /**
     * Whether the bot should forcefully update nicknames of users
     * who have linked their Minecraft account to their Discord account
     */
    private boolean forceMinecraftNicknameUpdate = true;
}
