package net.hypixel.nerdbot.bot;

import com.freya02.botcommands.api.CommandsBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.log4j.Log4j2;
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
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.feature.CurateFeature;
import net.hypixel.nerdbot.feature.GreenlitUpdateFeature;
import net.hypixel.nerdbot.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.feature.UserGrabberFeature;
import net.hypixel.nerdbot.listener.*;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.ForumChannelResolver;
import net.hypixel.nerdbot.util.discord.Users;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.Arrays;
import java.util.List;

@Log4j2
public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
            new HelloGoodbyeFeature(),
            new CurateFeature(),
            new UserGrabberFeature(),
            new GreenlitUpdateFeature()
    );

    private final Database database = new Database(System.getProperty("mongodb.uri"), "skyblock_nerds");
    private JDA jda;
    private BotConfig config;
    private long startTime;

    public NerdBot() {
    }

    @Override
    public void create(String[] args) throws LoginException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            e.printStackTrace(pw);
            ChannelManager.getLogChannel().sendMessage(jda.getRoleById(config.getBotManagerRoleId()).getAsMention() + "\n\n" + sw).queue();
        });

        loadConfig();

        JDABuilder builder = JDABuilder.createDefault(System.getProperty("bot.token"))
                .setEventManager(new AnnotatedEventManager())
                .addEventListeners(
                        new ModLogListener(),
                        new FeatureEventListener(),
                        new ActivityListener(),
                        new ReactionChannelListener()
                ).setActivity(Activity.of(config.getActivityType(), config.getActivity()));
        configureMemoryUsage(builder);

        if (config.getModMailConfig() != null) {
            builder.addEventListeners(new ModMailListener());
        }

        jda = builder.build();
        try {
            jda.awaitReady();
        } catch (InterruptedException exception) {
            log.error("Failed to create JDA instance!");
            exception.printStackTrace();
            System.exit(-1);
        }

        try {
            CommandsBuilder commandsBuilder = CommandsBuilder
                    .newBuilder(Long.parseLong(Users.AERH.getUserId()))
                    .extensionsBuilder(extensionsBuilder -> extensionsBuilder.registerParameterResolver(new ForumChannelResolver()));
            commandsBuilder.build(jda, "net.hypixel.nerdbot.command");
        } catch (IOException exception) {
            log.error("Couldn't create the command builder! Reason: " + exception.getMessage());
            System.exit(-1);
        }

        if (NerdBotApp.getBot().isReadOnly()) {
            log.info("Bot is loaded in read-only mode!");
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

        // Allow the bot to see guild message reactions
        builder.enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS);
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public void onStart() {
        for (BotFeature feature : FEATURES) {
            feature.onStart();
            log.info("Started feature " + feature.getClass().getSimpleName());
        }
        startTime = System.currentTimeMillis();
        log.info("Bot started in environment " + Environment.getEnvironment());
    }

    @Override
    public List<BotFeature> getFeatures() {
        return FEATURES;
    }

    @Override
    public void onEnd() {
        log.info("Shutting down Nerd Bot...");
        for (BotFeature feature : FEATURES) {
            feature.onEnd();
        }
        database.disconnect();
    }

    @Override
    public BotConfig getConfig() {
        return config;
    }

    @Override
    public Database getDatabase() {
        return database;
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
            log.info("Found config property: " + fileName);
        } else {
            log.info("Config property not defined, going to default path!");
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        try {
            log.info("Loading config file from '" + fileName + "'");
            File file = new File(fileName);
            config = (BotConfig) Util.jsonToObject(file, BotConfig.class);

            log.info("Loaded config from " + file.getAbsolutePath());
            log.info(config.toString());
        } catch (FileNotFoundException exception) {
            log.error("Could not find config file " + fileName);
            System.exit(-1);
        }
    }

    @Override
    public boolean writeConfig(@NotNull BotConfig newConfig) {
        //Get the location that we're saving the config at
        String fileName;
        if (System.getProperty("bot.config") != null) {
            fileName = System.getProperty("bot.config");
            log.info("Found config property: " + fileName);
        } else {
            log.info("Config property not defined, going to default path!");
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        Gson jsonConfig = new GsonBuilder().setPrettyPrinting().create();
        //Actually write the new config
        try {
            //SPECIAL NOTE: You need to close the FileWriter or else it doesn't write the whole config :)
            FileWriter fw = new FileWriter(fileName);
            jsonConfig.toJson(newConfig, fw);
            fw.close();
            return true;
        } catch (IOException e) {
            log.error("Could not save config file.");
            e.printStackTrace();
            return false;
        }
    }
}
