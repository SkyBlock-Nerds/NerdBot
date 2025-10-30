package net.hypixel.nerdbot.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.SlashCommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.activity.ActivityListener;
import net.hypixel.nerdbot.activity.ActivityPurgeFeature;
import net.hypixel.nerdbot.api.badge.BadgeManager;
import net.hypixel.nerdbot.api.bot.DiscordBot;
import net.hypixel.nerdbot.api.bot.Environment;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.api.repository.Repository;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.cache.MessageCache;
import net.hypixel.nerdbot.cache.suggestion.SuggestionCache;
import net.hypixel.nerdbot.config.BotConfig;
import net.hypixel.nerdbot.discord.config.AlphaProjectConfigUpdater;
import net.hypixel.nerdbot.discord.feature.CurateFeature;
import net.hypixel.nerdbot.discord.feature.HelloGoodbyeFeature;
import net.hypixel.nerdbot.discord.feature.ProfileUpdateFeature;
import net.hypixel.nerdbot.discord.feature.UserGrabberFeature;
import net.hypixel.nerdbot.discord.feature.UserNominationFeature;
import net.hypixel.nerdbot.discord.listener.FunListener;
import net.hypixel.nerdbot.discord.listener.MetricsListener;
import net.hypixel.nerdbot.discord.listener.ModLogListener;
import net.hypixel.nerdbot.discord.listener.PinListener;
import net.hypixel.nerdbot.discord.listener.ReactionChannelListener;
import net.hypixel.nerdbot.discord.listener.RoleRestrictedChannelListener;
import net.hypixel.nerdbot.discord.listener.SuggestionListener;
import net.hypixel.nerdbot.discord.modmail.ModMailListener;
import net.hypixel.nerdbot.discord.reminder.ReminderDispatcher;
import net.hypixel.nerdbot.discord.user.BirthdayScheduler;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.urlwatcher.HypixelThreadURLWatcher;
import net.hypixel.nerdbot.urlwatcher.URLWatcher;
import net.hypixel.nerdbot.urlwatcher.handler.firesale.FireSaleDataHandler;
import net.hypixel.nerdbot.urlwatcher.handler.status.StatusPageDataHandler;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.JsonUtils;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NerdBot implements DiscordBot {

    private static final List<BotFeature> FEATURES = Arrays.asList(
        new HelloGoodbyeFeature(),
        new CurateFeature(),
        new UserGrabberFeature(),
        new ProfileUpdateFeature(),
        new ActivityPurgeFeature(),
        new UserNominationFeature()
    );

    private final Database database = new Database(System.getProperty("db.mongodb.uri", "mongodb://localhost:27017/"), "skyblock_nerds");
    private JDA jda;
    private BotConfig config;
    @Getter
    private SuggestionCache suggestionCache;
    @Getter
    private MessageCache messageCache;
    private long startTime;

    public NerdBot() {
    }

    @Override
    public void onStart() {
        if (config.getBadgeConfig().getBadges() != null) {
            BadgeManager.loadBadges();
        } else {
            log.warn("No badges found in config file, so no badges will be loaded!");
        }

        DiscordUserRepository discordUserRepository = database.getRepositoryManager().getRepository(DiscordUserRepository.class);
        if (discordUserRepository != null) {
            discordUserRepository.loadAllDocumentsIntoCacheAsync()
                .thenRun(() -> {
                    discordUserRepository.forEach(BirthdayScheduler::schedule);
                    log.info("Successfully loaded all Discord users into cache");
                })
                .exceptionally(throwable -> {
                    log.error("Failed to load Discord users into cache", throwable);
                    return null;
                });
        }

        loadRemindersFromDatabase();
        startUrlWatchers();

        DiscordUtils.getMainGuild().loadMembers()
            .onSuccess(members -> PrometheusMetrics.TOTAL_USERS_AMOUNT.set(members.size()))
            .onError(throwable -> log.error("Failed to load members!", throwable));

        if (config.getMetricsConfig().isEnabled()) {
            PrometheusMetrics.setMetricsEnabled(true);
        }

        for (BotFeature feature : FEATURES) {
            feature.onFeatureStart();
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

        FEATURES.forEach(BotFeature::onFeatureEnd);

        try {
            Map<Class<?>, Object> repositories = database.getRepositoryManager().getRepositories();
            log.info("Saving data from " + database.getRepositoryManager().getRepositories().size() + " repositories...");

            repositories.forEach((aClass, o) -> {
                Repository<?> repository = (Repository<?>) o;
                repository.saveAllToDatabaseAsync()
                    .thenAccept(result -> {
                        if (result != null && result.wasAcknowledged()) {
                            int total = result.getInsertedCount() + result.getModifiedCount();
                            log.info("Saved {} documents to database for repository {} ({} inserted, {} modified, {} deleted)",
                                total, repository.getClass().getSimpleName(), result.getInsertedCount(), result.getModifiedCount(), result.getDeletedCount());
                        } else {
                            log.info("Saved 0 documents to database for repository {}", repository.getClass().getSimpleName());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to save documents for repository {}", repository.getClass().getSimpleName(), throwable);
                        return null;
                    })
                    .join(); // Wait for completion during shutdown
            });
        } catch (Exception exception) {
            log.error("Error while saving data: " + exception.getMessage(), exception);
        } finally {
            database.getMongoClient().close();
        }

        JsonUtils.shutdown();

        log.info("Bot shutdown complete!");
    }

    @Override
    public long getStartTime() {
        return startTime;
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
                new SuggestionListener(),
                new ReactionChannelListener(),
                new PinListener(),
                new MetricsListener(),
                new FunListener(),
                new RoleRestrictedChannelListener()
            )
            .setActivity(Activity.of(Activity.ActivityType.valueOf(config.getActivityType().name()), config.getActivity()));

        configureMemoryUsage(builder);

        if (config.getModMailConfig() != null) {
            builder.addEventListeners(new ModMailListener());
        }

        jda = builder.build();

        try {
            jda.awaitReady();
            jda.addEventListener(new EmojiCache(), new ChannelCache());
        } catch (InterruptedException exception) {
            log.error("Failed to create JDA instance!", exception);
            System.exit(-1);
        }

        AlphaProjectConfigUpdater.updateForumIds(config, true, true, jda.getForumChannels());
        messageCache = new MessageCache();
        suggestionCache = new SuggestionCache();

        SlashCommandManager.builder()
            .withJDA(jda)
            .scanPackage("net.hypixel.nerdbot.discord.command")
            .build();

        BotEnvironment.getBot().onStart();
        log.info("Bot is ready!");

        if (BotEnvironment.getBot().isReadOnly()) {
            log.info("\n!!! BOT IS LOADED IN READ-ONLY MODE !!!\n");
        }
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

        reminderRepository.loadAllDocumentsIntoCacheAsync()
            .thenRun(() -> {
                reminderRepository.forEach(reminder -> {
                    long now = System.currentTimeMillis();

                    if (now > reminder.getTime()) {
                        ReminderDispatcher.dispatch(reminder, true);
                        log.info("Sent reminder {} because it was not sent yet!", reminder);
                        return;
                    }

                    ReminderDispatcher.schedule(reminder);
                    log.info("Loaded reminder: {}", reminder);
                });

                log.info("Loaded {} reminders!", reminderRepository.getCache().estimatedSize());
            })
            .exceptionally(throwable -> {
                log.error("Failed to load reminders from database", throwable);
                return null;
            });
    }

    private void startUrlWatchers() {
        URLWatcher statusPageWatcher = new URLWatcher("https://status.hypixel.net/api/v2/summary.json");
        URLWatcher fireSaleWatcher = new URLWatcher("https://api.hypixel.net/skyblock/firesales");
        HypixelThreadURLWatcher skyBlockPatchNotesWatcher = new HypixelThreadURLWatcher("https://hypixel.net/forums/skyblock-patch-notes.158/.rss");
        HypixelThreadURLWatcher hypixelNewsWatcher = new HypixelThreadURLWatcher("https://hypixel.net/forums/news-and-announcements.4/.rss");

        statusPageWatcher.startWatching(1, TimeUnit.MINUTES, new StatusPageDataHandler());
        fireSaleWatcher.startWatching(1, TimeUnit.MINUTES, new FireSaleDataHandler());
        hypixelNewsWatcher.startWatching(1, TimeUnit.MINUTES);
        skyBlockPatchNotesWatcher.startWatching(1, TimeUnit.MINUTES);
    }


        private void configureMemoryUsage(JDABuilder builder) {
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

        log.info("Loading config file from '{}'", fileName);
        File file = new File(fileName);
        JsonUtils.jsonToObjectAsync(file, BotConfig.class)
            .thenAccept(loadedConfig -> {
                config = (BotConfig) loadedConfig;
                log.info("Loaded config from {}", file.getAbsolutePath());
            })
            .exceptionally(throwable -> {
                log.error("Failed to load config from {}", file.getAbsolutePath(), throwable);
                System.exit(-1);
                return null;
            })
            .join(); // Wait for completion during startup
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
        } catch (IOException exception) {
            log.error("Could not save config file!", exception);
            return false;
        }
    }
}