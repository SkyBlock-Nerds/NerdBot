package net.hypixel.nerdbot.discord.util;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.bot.DiscordBot;

@UtilityClass
public class DiscordBotEnvironment {

    public DiscordBot getBot() {
        return (DiscordBot) BotEnvironment.getBot();
    }
}