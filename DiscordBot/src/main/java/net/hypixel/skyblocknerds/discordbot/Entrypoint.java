package net.hypixel.skyblocknerds.discordbot;

public class SkyBlockNerdsBot extends DiscordBot {

    public static final DiscordBot INSTANCE = new SkyBlockNerdsBot();

    public static void main(String[] args) throws Exception {
        INSTANCE.start(args);
    }
}
