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

    public static Optional<TextChannel> getChannelById(String channel) {
        return Optional.ofNullable(NerdBotApp.getBot().getJDA().getTextChannelById(channel));
    }

    public static Optional<TextChannel> getChannelByName(String name) {
        return NerdBotApp.getBot().getJDA().getTextChannelsByName(name, true).stream().findFirst();
    }

    public static Optional<ForumChannel> getModMailChannel() {
        return Optional.ofNullable(NerdBotApp.getBot().getJDA().getForumChannelById(NerdBotApp.getBot().getConfig().getModMailConfig().getChannelId()));
    }

    public static Optional<TextChannel> getLogChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getLogChannelId());
    }

    public static Optional<TextChannel> getVerifyLogChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getChannelConfig().getVerifyLogChannelId());
    }

    public static Optional<TextChannel> getRequestedReviewChannel() {
        return getChannelById(NerdBotApp.getBot().getConfig().getSuggestionConfig().getRequestedReviewForumId());
    }
}
