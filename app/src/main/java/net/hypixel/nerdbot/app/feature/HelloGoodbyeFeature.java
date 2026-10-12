package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.awt.Color;
import java.time.Clock;

/** Posts to the log channel when the bot starts and stops. */
public class HelloGoodbyeFeature extends BotFeature {

    private final LogChannel logChannel;
    private final Clock clock;

    @Inject
    public HelloGoodbyeFeature(LogChannel logChannel, Clock clock) {
        this.logChannel = logChannel;
        this.clock = clock;
    }

    @Override
    public void onFeatureStart() {
        logChannel.send(hello());
    }

    @Override
    public void onFeatureEnd() {
        logChannel.send(goodbye());
    }

    // Built when sent, so the timestamp is when it happened rather than when the class loaded
    MessageEmbed hello() {
        return new EmbedBuilder()
            .setTitle("Hello there!")
            .setDescription("It would appear as though my core functions are operating at peak efficiency!")
            .setImage("https://media2.giphy.com/media/xTiIzJSKB4l7xTouE8/giphy.gif")
            .setColor(Color.GREEN)
            .setTimestamp(clock.instant())
            .build();
    }

    MessageEmbed goodbye() {
        return new EmbedBuilder()
            .setTitle("Goodbye cruel world!")
            .setDescription("It seems as though I'm needed elsewhere!")
            .setImage("https://i.pinimg.com/564x/7c/5a/19/7c5a193b0f832bb13a2b1dd802a023ab.jpg")
            .setColor(Color.GREEN)
            .setTimestamp(clock.instant())
            .build();
    }
}
