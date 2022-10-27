package net.hypixel.nerdbot.bot;

import com.freya02.botcommands.api.CommandsBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.feature.CurateFeature;
import net.hypixel.nerdbot.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.feature.UserGrabberFeature;
import net.hypixel.nerdbot.listener.ActivityListener;
import net.hypixel.nerdbot.listener.MessageListener;
import net.hypixel.nerdbot.listener.ShutdownListener;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.ForumChannelResolver;
import net.hypixel.nerdbot.util.Users;
import net.hypixel.nerdbot.util.Util;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
            new HelloGoodbyeFeature(),
            new CurateFeature(),
            new UserGrabberFeature()
    );

    private JDA jda;
    private BotConfig config;
    private long startTime;

    public NerdBot() {
    }

    @Override
    public void create(String[] args) throws LoginException {
        loadConfig();

        JDABuilder builder = JDABuilder.createDefault(System.getProperty("bot.token"))
                .setEventManager(new AnnotatedEventManager())
                .addEventListeners(new MessageListener(), new FeatureEventListener(), new ShutdownListener(), new ActivityListener())
                .setActivity(Activity.of(config.getActivityType(), config.getActivity()));
        configureMemoryUsage(builder);

        jda = builder.build();
        try {
            jda.awaitReady();
        } catch (InterruptedException exception) {
            NerdBotApp.LOGGER.error("Failed to create JDA instance!");
            exception.printStackTrace();
            System.exit(-1);
        }

        try {
            CommandsBuilder commandsBuilder = CommandsBuilder
                    .newBuilder(Long.parseLong(Users.AERH.getUserId()))
                    .extensionsBuilder(extensionsBuilder ->
                            extensionsBuilder.registerParameterResolver(new ForumChannelResolver())
                    );
            commandsBuilder.build(jda, "net.hypixel.nerdbot.command");
        } catch (IOException exception) {
            NerdBotApp.LOGGER.error("Couldn't create the command builder! Reason: " + exception.getMessage());
            System.exit(-1);
        }

        if (NerdBotApp.getBot().isReadOnly()) {
            NerdBotApp.LOGGER.info("Bot is loaded in read-only mode!");
        }

        NerdBotApp.getBot().onStart();
    }

    @Override
    public void configureMemoryUsage(JDABuilder builder) {
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);

        // Disable cache for member activities (streaming/games/spotify)
        builder.disableCache(CacheFlag.ACTIVITY);

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
            NerdBotApp.LOGGER.info("Started feature " + feature.getClass().getSimpleName());
        }
        startTime = System.currentTimeMillis();
        NerdBotApp.LOGGER.info("Bot started on region " + Environment.getEnvironment());
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
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public boolean isReadOnly() {
        return System.getProperty("bot.readOnly") != null && Boolean.parseBoolean(System.getProperty("bot.readOnly"));
    }

    @Override
    public void loadConfig() {
        String fileName;
        if (System.getProperty("bot.config") != null) {
            fileName = System.getProperty("bot.config");
        } else {
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        try {
            File file = new File(fileName);
            config = Util.loadConfig(file);

            NerdBotApp.LOGGER.info("Loaded config from " + file.getAbsolutePath());
            NerdBotApp.LOGGER.debug(config.toString());
        } catch (FileNotFoundException exception) {
            NerdBotApp.LOGGER.error("Could not find config file " + fileName);
            System.exit(-1);
        }
    }
}
