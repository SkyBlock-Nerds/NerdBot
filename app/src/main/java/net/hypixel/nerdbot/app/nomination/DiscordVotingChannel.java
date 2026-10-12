package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DiscordVotingChannel implements VotingChannel {

    private final JDA jda;
    private final Provider<NerdBotConfig> config;

    @Inject
    public DiscordVotingChannel(JDA jda, Provider<NerdBotConfig> config) {
        this.jda = jda;
        this.config = config;
    }

    @Override
    public void requireAvailable() {
        channel();
    }

    @Override
    public CompletableFuture<Void> send(MessageEmbed embed) {
        return channel().sendMessageEmbeds(embed).submit().thenApply(sent -> null);
    }

    private TextChannel channel() {
        String channelId = config.get().getChannelConfig().getMemberVotingChannelId();
        // JDA throws on an id that isn't a number, which a blank config value would be
        return Optional.ofNullable(channelId)
            .filter(id -> id.matches("\\d+"))
            .map(jda::getTextChannelById)
            .orElseThrow(() -> new IllegalStateException("The member voting channel (" + channelId + ") can't be found"));
    }
}
