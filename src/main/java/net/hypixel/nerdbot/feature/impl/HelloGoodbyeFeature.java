package net.hypixel.nerdbot.feature.impl;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.feature.BotFeature;

import java.awt.*;
import java.time.OffsetDateTime;

public class HelloGoodbyeFeature extends BotFeature {

    private static final MessageEmbed HELLO_THERE = new EmbedBuilder()
            .setTitle("Hello there!")
            .setDescription("It would appear as though my core functions are operating at peak efficiency!")
            .setImage("https://media2.giphy.com/media/xTiIzJSKB4l7xTouE8/giphy.gif")
            .setColor(Color.GREEN)
            .setTimestamp(OffsetDateTime.now())
            .build();

    private static final MessageEmbed GOODBYE = new EmbedBuilder()
            .setTitle("Goodbye cruel world!")
            .setDescription("It seems as though I'm needed elsewhere!")
            .setImage("https://i.pinimg.com/564x/7c/5a/19/7c5a193b0f832bb13a2b1dd802a023ab.jpg")
            .setColor(Color.RED)
            .setTimestamp(OffsetDateTime.now())
            .build();

    @Override
    public void onStart() {
        TextChannel channel = ChannelManager.getChannel(Channel.CURATE);
        if (channel != null) channel.sendMessageEmbeds(HELLO_THERE).queue();
    }

    @Override
    public void onEnd() {
        TextChannel channel = ChannelManager.getChannel(Channel.CURATE);
        if (channel != null) channel.sendMessageEmbeds(GOODBYE).queue();
    }

}
