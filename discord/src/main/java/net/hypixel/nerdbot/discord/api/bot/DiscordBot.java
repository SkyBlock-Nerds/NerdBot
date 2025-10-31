package net.hypixel.nerdbot.discord.api.bot;

import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.cache.suggestion.SuggestionCache;
import net.hypixel.nerdbot.core.api.bot.Bot;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import org.jetbrains.annotations.NotNull;

public interface DiscordBot extends Bot {

    JDA getJDA();

    MessageCache getMessageCache();

    SuggestionCache getSuggestionCache();

    long getStartTime();

    DiscordBotConfig getConfig();

    boolean writeConfig(@NotNull DiscordBotConfig newConfig);

}
