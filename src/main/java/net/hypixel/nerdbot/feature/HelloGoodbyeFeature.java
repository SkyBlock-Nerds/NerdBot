package net.hypixel.nerdbot.feature;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.channel.ChannelManager;

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

    @Override
    public void onStart() {
        if (ChannelManager.getLogChannel() != null) {
            ChannelManager.getLogChannel().sendMessageEmbeds(HELLO_THERE).queue();
        }
    }

    @Override
    public void onEnd() {
        // Goodbye message does not work because the bot is offline by the time this is called
    }
}
