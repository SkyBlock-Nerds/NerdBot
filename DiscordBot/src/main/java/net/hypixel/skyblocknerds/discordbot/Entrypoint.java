package net.hypixel.skyblocknerds.discordbot;

public class Entrypoint extends DiscordBot {

    public static final DiscordBot INSTANCE = new Entrypoint();

    public static void main(String[] args) throws Exception {
        INSTANCE.start(args);
    }
}
