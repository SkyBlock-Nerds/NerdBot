package net.hypixel.nerdbot.api.bot;

import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.cache.MessageCache;
import net.hypixel.nerdbot.cache.suggestion.SuggestionCache;
import net.hypixel.nerdbot.config.DiscordBotConfig;
import org.jetbrains.annotations.NotNull;

public interface DiscordBot extends Bot {

    JDA getJDA();

    MessageCache getMessageCache();

    SuggestionCache getSuggestionCache();

    long getStartTime();

    DiscordBotConfig getConfig();

    boolean writeConfig(@NotNull DiscordBotConfig newConfig);

}