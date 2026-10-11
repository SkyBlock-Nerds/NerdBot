package net.hypixel.nerdbot.app.channel;

import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * The staff log channel from {@code channelConfig.logChannelId}.
 */
public interface LogChannel {

    void send(MessageEmbed embed);
}
