package net.hypixel.nerdbot.app.channel;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.discord.cache.ChannelCache;

public class DiscordLogChannel implements LogChannel {

    @Inject
    public DiscordLogChannel() {
    }

    @Override
    public void send(MessageEmbed embed) {
        ChannelCache.sendToLogChannel(embed);
    }
}
