package net.hypixel.nerdbot.bot.impl;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.bot.Bot;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.feature.BotFeature;
import net.hypixel.nerdbot.feature.impl.CurateFeature;
import net.hypixel.nerdbot.listener.ReadyListener;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
            new CurateFeature()
    );
    public static final MessageEmbed HELLO_THERE = new EmbedBuilder()
            .setTitle("Hello there!")
            .setDescription("It would appear as though my core functions are operating at peak efficiency!")
            .setImage("https://media2.giphy.com/media/xTiIzJSKB4l7xTouE8/giphy.gif")
            .setColor(Color.GREEN)
            .setTimestamp(OffsetDateTime.now())
            .build();

    private JDA jda;

    public NerdBot() {
    }

    @Override
    public void create(String[] args) throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(args[0])
                .disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .setActivity(Activity.watching("You"));

        configureMemoryUsage(builder);

        jda = builder.build();
    }

    @Override
    public void configureMemoryUsage(JDABuilder builder) {
        // Disable cache for member activities (streaming/games/spotify)
        builder.disableCache(CacheFlag.ACTIVITY);

        // Only cache members who are either in a voice channel or owner of the guild
        builder.setMemberCachePolicy(MemberCachePolicy.VOICE.or(MemberCachePolicy.OWNER));

        // Disable member chunking on startup
        builder.setChunkingFilter(ChunkingFilter.NONE);

        // Disable presence updates and typing events
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public void onStart() {
        TextChannel channel = ChannelManager.getChannel(Channel.CURATE);
        if (channel != null) {
            channel.sendMessageEmbeds(HELLO_THERE).queue();
        }

        for (BotFeature feature : FEATURES) {
            feature.onStart();
        }
    }

    @Override
    public void registerListeners() {
        jda.addEventListener(new ReadyListener());
    }

    @Override
    public void onEnd() {
        for (BotFeature feature : FEATURES) {
            feature.onEnd();
        }
    }
}
