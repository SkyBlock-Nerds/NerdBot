package net.hypixel.nerdbot.discord.util;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;

@UtilityClass
public class DiscordBotEnvironment {

    public DiscordBot getBot() {
        return BotEnvironment.getBot(DiscordBot.class);
    }
}
