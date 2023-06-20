package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class SuggestionListener {

    @SubscribeEvent
    public void onThreadCreateEvent(@NotNull ChannelCreateEvent event) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            NerdBotApp.getSuggestionCache().addSuggestion(event.getChannel().asThreadChannel());
        }
    }

    @SubscribeEvent
    public void onThreadDeleteEvent(@NotNull ChannelDeleteEvent event) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            NerdBotApp.getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
        }
    }
}
