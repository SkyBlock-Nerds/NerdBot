package net.hypixel.skyblocknerds.discordbot.configuration;

public class MojangConfiguration {

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
