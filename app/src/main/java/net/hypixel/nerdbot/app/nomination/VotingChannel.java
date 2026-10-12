package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.concurrent.CompletableFuture;

/** The channel from {@code channelConfig.memberVotingChannelId} that nominations and inactivity warnings go to. */
public interface VotingChannel {

    /**
     * @throws IllegalStateException when the channel can't be found, so a sweep stops before checking anyone
     */
    void requireAvailable();

    /** Completes once Discord has accepted the message, and fails if it didn't. */
    CompletableFuture<Void> send(MessageEmbed embed);
}
