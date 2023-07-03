package net.hypixel.nerdbot.channel;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;

import javax.annotation.Nullable;

@Log4j2
public class ChannelManager {

    private ChannelManager() {
    }

    @Nullable
    public static TextChannel getChannel(String channel) {
        TextChannel textChannel = NerdBotApp.getBot().getJDA().getTextChannelById(channel);
        if (textChannel == null) {
            log.error("Failed to find channel: '" + channel + "'");
            return null;
        }
        return textChannel;
    }

    @Nullable
    public static TextChannel getLogChannel() {
        return getChannel(NerdBotApp.getBot().getConfig().getLogChannel());
    }
}
