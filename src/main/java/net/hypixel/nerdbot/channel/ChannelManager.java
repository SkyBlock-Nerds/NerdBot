package net.hypixel.nerdbot.channel;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;

import java.util.Optional;

@Log4j2
public class ChannelManager {

    private ChannelManager() {
    }

    public static Optional<TextChannel> getChannel(String channel) {
        return Optional.ofNullable(NerdBotApp.getBot().getJDA().getTextChannelById(channel));
    }

    public static Optional<ForumChannel> getModMailChannel() {
        return getChannel(NerdBotApp.getBot().getConfig().getModMailConfig().getChannelId())
            .map(ForumChannel.class::cast);
    }

    public static Optional<TextChannel> getLogChannel() {
        return getChannel(NerdBotApp.getBot().getConfig().getChannelConfig().getLogChannelId());
    }

    public static Optional<TextChannel> getVerifyLogChannel() {
        return getChannel(NerdBotApp.getBot().getConfig().getChannelConfig().getVerifyLogChannelId());
    }
}
