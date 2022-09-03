package net.hypixel.nerdbot.bot;

import net.aerh.jdacommands.CommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.config.BotConfig;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.feature.CurateFeature;
import net.hypixel.nerdbot.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.feature.UserGrabberFeature;
import net.hypixel.nerdbot.listener.MessageListener;
import net.hypixel.nerdbot.listener.ShutdownListener;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Region;
import net.hypixel.nerdbot.util.Util;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
            new HelloGoodbyeFeature(),
            new CurateFeature(),
            new UserGrabberFeature()
    );

    private JDA jda;
    private BotConfig config;
    private CommandManager commands;

    private long startTime;

    public NerdBot() {
    }

    @Override
    public void create(String[] args) throws LoginException {
        JDABuilder builder = JDABuilder.createDefault(System.getProperty("bot.token"))
                .addEventListeners(new MessageListener(), new FeatureEventListener(), new ShutdownListener())
                .setActivity(Activity.competing("mc.hypixel.net"));

        configureMemoryUsage(builder);

        jda = builder.build();
        try {
            jda.awaitReady();
        } catch (InterruptedException exception) {
            Logger.error("Failed to create JDA instance!");
            exception.printStackTrace();
            System.exit(0);
        }

        String fileName = Region.getRegion().name().toLowerCase() + ".config.json";
        try {
            File file;

            if (Region.isDev()) {
                file = new File(Objects.requireNonNull(getClass().getClassLoader().getResource(fileName)).getFile());
            } else {
                file = new File(fileName);
            }

            config = Util.loadConfig(file);
            Logger.info("Loaded config from " + file.getAbsolutePath());
        } catch (FileNotFoundException exception) {
            Logger.error("Could not find config file " + fileName);
            System.exit(-1);
        }

        commands = new CommandManager(jda);
        commands.registerCommandsInPackage("net.hypixel.nerdbot.command");

        if (Boolean.parseBoolean(System.getProperty("bot.readOnly"))) {
            Logger.info("Bot is loaded in read-only mode!");
        }

        NerdBotApp.getBot().onStart();
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

        // Allow the bot to see all guild members
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS);

        // Enable the bot to see message content
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public void onStart() {
        for (BotFeature feature : FEATURES) {
            feature.onStart();
            Logger.info("Started feature " + feature.getClass().getSimpleName());
        }
        startTime = System.currentTimeMillis();
        Logger.info("Bot started on region " + Region.getRegion());
    }

    @Override
    public List<BotFeature> getFeatures() {
        return FEATURES;
    }

    @Override
    public void onEnd() {
        for (BotFeature feature : FEATURES) feature.onEnd();
        Database.getInstance().disconnect();
    }

    @Override
    public BotConfig getConfig() {
        return config;
    }

    @Override
    public CommandManager getCommands() {
        return commands;
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

}
