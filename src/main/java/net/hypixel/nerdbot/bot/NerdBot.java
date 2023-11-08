package net.hypixel.nerdbot.bot;

import com.freya02.botcommands.api.CommandsBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.client.result.InsertManyResult;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.feature.CurateFeature;
import net.hypixel.nerdbot.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.feature.ProfileUpdateFeature;
import net.hypixel.nerdbot.feature.UserGrabberFeature;
import net.hypixel.nerdbot.listener.*;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.ForumChannelResolver;
import net.hypixel.nerdbot.util.watcher.URLWatcher;
import net.hypixel.nerdbot.util.watcher.handlers.FireSaleDataHandler;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Log4j2
public class NerdBot implements Bot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
        new HelloGoodbyeFeature(),
        new CurateFeature(),
        new UserGrabberFeature(),
        new ProfileUpdateFeature()
    );

    private final Database database = new Database(System.getProperty("mongodb.uri"), "skyblock_nerds");
    private JDA jda;
    private BotConfig config;
    private long startTime;

    public NerdBot() {
    }

    @Override
    public void onStart() {
        for (BotFeature feature : FEATURES) {
            feature.onFeatureStart();
            log.info("Started feature " + feature.getClass().getSimpleName());
        }

        loadRemindersFromDatabase();
        startUrlWatchers();

        Util.getMainGuild().loadMembers()
            .onSuccess(members -> PrometheusMetrics.TOTAL_USERS_AMOUNT.set(members.size()))
            .onError(Throwable::printStackTrace);

        if (config.getMetricsConfig().isEnabled()) {
            PrometheusMetrics.setMetricsEnabled(true);
        }

        startTime = System.currentTimeMillis();
        log.info("Bot started in environment " + Environment.getEnvironment());
    }

    @Override
    public void onEnd() {
        log.info("Shutting down Nerd Bot...");

        FEATURES.forEach(BotFeature::onFeatureEnd);

        try {
            Map<Class<?>, Object> repositories = database.getRepositoryManager().getRepositories();
            log.info("Saving data from " + database.getRepositoryManager().getRepositories().size() + " repositories...");

            repositories.forEach((aClass, o) -> {
                Repository<?> repository = (Repository<?>) o;
                InsertManyResult result = repository.saveAllToDatabase();

                if (result != null && result.wasAcknowledged()) {
                    log.info("Saved " + result.getInsertedIds().size() + " documents to database for repository " + repository.getClass().getSimpleName());
                } else {
                    log.info("Saved 0 documents to database for repository " + repository.getClass().getSimpleName());
                }
            });
        } catch (Exception e) {
            log.error("Error while saving data: " + e.getMessage(), e);
        } finally {
            database.getMongoClient().close();
        }

        log.info("Bot shutdown complete!");
    }

    @Override
    public void create(String[] args) throws LoginException {
        loadConfig();

        JDABuilder builder = JDABuilder.createDefault(System.getProperty("bot.token"))
            .setEventManager(new AnnotatedEventManager())
            .addEventListeners(
                new ModLogListener(),
                new FeatureEventListener(),
                new ActivityListener(),
                new ReactionChannelListener(),
                new SuggestionListener(),
                new VerificationListener(),
                new PinListener(),
                new MetricsListener()
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
                .newBuilder()
                .addOwners(config.getOwnerIds())
                .extensionsBuilder(extensionsBuilder -> extensionsBuilder
                    .registerParameterResolver(new ForumChannelResolver())
                    .registerAutocompletionTransformer(ForumChannel.class, forumChannel -> new Command.Choice(forumChannel.getName(), forumChannel.getId()))
                    .registerAutocompletionTransformer(ForumTag.class, forumTag -> new Command.Choice(forumTag.getName(), forumTag.getId()))
                );
            commandsBuilder.build(jda, "net.hypixel.nerdbot.command");
        } catch (IOException exception) {
            log.error("Couldn't create the command builder! Reason: " + exception.getMessage());
            System.exit(-1);
        }

        if (NerdBotApp.getBot().isReadOnly()) {
            log.info("Bot is loaded in read-only mode!");
        }

        NerdBotApp.getBot().onStart();

        log.info("Bot is ready!");
    }

    private void loadRemindersFromDatabase() {
        if (!database.isConnected()) {
            log.error("Failed to load reminders from database, database is not connected!");
            return;
        }

        log.info("Loading all reminders from database...");

        ReminderRepository reminderRepository = database.getRepositoryManager().getRepository(ReminderRepository.class);
        if (reminderRepository == null) {
            log.error("Failed to load reminders from database, repository is null!");
            return;
        }

        reminderRepository.loadAllDocumentsIntoCache();

        reminderRepository.forEach(reminder -> {
            Date now = new Date();

            if (now.after(reminder.getTime())) {
                reminder.sendReminder(true);
                log.info("Sent reminder " + reminder + " because it was not sent yet!");
                return;
            }

            reminder.schedule();
            log.info("Loaded reminder: " + reminder);
        });

        log.info("Loaded " + reminderRepository.getCache().estimatedSize() + " reminders!");
    }

    private void startUrlWatchers() {
        TextChannel announcementChannel = ChannelManager.getChannel(config.getChannelConfig().getAnnouncementChannelId());

        if (announcementChannel == null) {
            log.error("Couldn't find announcement channel!");
            return;
        }

        URLWatcher fireSaleWatcher = new URLWatcher("https://api.hypixel.net/skyblock/firesales");
        fireSaleWatcher.startWatching(1, TimeUnit.MINUTES, new FireSaleDataHandler());
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

    public List<BotFeature> getFeatures() {
        return FEATURES;
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
            config = (BotConfig) JsonUtil.jsonToObject(file, BotConfig.class);

            log.info("Loaded config from " + file.getAbsolutePath());
        } catch (FileNotFoundException exception) {
            log.error("Could not find config file " + fileName);
            System.exit(-1);
        }
    }

    @Override
    public boolean writeConfig(@NotNull BotConfig newConfig) {
        // Get the location that we're saving the config at
        String fileName;
        if (System.getProperty("bot.config") != null) {
            fileName = System.getProperty("bot.config");
            log.info("Found config property: " + fileName);
        } else {
            log.info("Config property not defined, going to default path!");
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        Gson jsonConfig = new GsonBuilder().setPrettyPrinting().create();
        // Actually write the new config
        try {
            // SPECIAL NOTE: You need to close the FileWriter or else it doesn't write the whole config :)
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
