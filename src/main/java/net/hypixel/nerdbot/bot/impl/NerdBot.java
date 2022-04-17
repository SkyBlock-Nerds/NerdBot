package net.hypixel.nerdbot.bot.impl;

import com.github.kaktushose.jda.commands.JDACommands;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.Bot;
import net.hypixel.nerdbot.config.BotConfig;
import net.hypixel.nerdbot.feature.BotFeature;
import net.hypixel.nerdbot.feature.impl.CurateFeature;
import net.hypixel.nerdbot.feature.impl.HelloGoodbyeFeature;
import net.hypixel.nerdbot.listener.ReadyListener;
import net.hypixel.nerdbot.listener.ShutdownListener;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Util;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;

public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
            new HelloGoodbyeFeature(),
            new CurateFeature()
    );

    private JDA jda;
    private JDACommands commands;
    private BotConfig config;

    public NerdBot() {
    }

    @Override
    public void create(String[] args) throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(args[0])
                .disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .setActivity(Activity.playing("play.hypixel.net"));

        configureMemoryUsage(builder);

        jda = builder.build();
        commands = JDACommands.start(jda, NerdBotApp.class);

        try {
            config = Util.loadConfig(new File(getClass().getClassLoader().getResource("example-config.json").getFile()));
        } catch (FileNotFoundException exception) {
            Logger.error("Could not find config file!");
            System.exit(-1);
        }
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
        for (BotFeature feature : FEATURES) {
            feature.onStart();
        }
    }

    @Override
    public void registerListeners() {
        jda.addEventListener(new ReadyListener(), new ShutdownListener());
    }

    @Override
    public void onEnd() {
        for (BotFeature feature : FEATURES) {
            feature.onEnd();
        }
        commands.shutdown();
    }

    @Override
    public BotConfig getConfig() {
        return config;
    }
}
