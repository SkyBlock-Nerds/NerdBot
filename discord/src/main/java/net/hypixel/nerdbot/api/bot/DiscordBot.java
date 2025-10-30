package net.hypixel.nerdbot.api.bot;

import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.cache.MessageCache;
import net.hypixel.nerdbot.cache.suggestion.SuggestionCache;

public interface DiscordBot extends Bot {

    JDA getJDA();

    MessageCache getMessageCache();

    SuggestionCache getSuggestionCache();

    long getStartTime();

}