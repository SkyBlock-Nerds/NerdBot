package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class SuggestionListener {

    @SubscribeEvent
    public void onThreadCreateEvent(@NotNull ChannelCreateEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getSuggestionCache().addSuggestion(event.getChannel().asThreadChannel());
        }
    }

    @SubscribeEvent
    public void onThreadDeleteEvent(@NotNull ChannelDeleteEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
        }
    }

    private boolean isInSuggestionChannel(GenericChannelEvent event) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

            return Util.safeArrayStream(suggestionConfig.getSuggestionForumIds(), suggestionConfig.getAlphaSuggestionForumIds())
                .anyMatch(channelId -> channelId.equals(forumChannelId));
        }

        return false;
    }
}
