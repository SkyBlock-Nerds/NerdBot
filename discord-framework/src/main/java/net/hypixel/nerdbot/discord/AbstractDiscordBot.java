package net.hypixel.nerdbot.discord;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
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
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;
import net.hypixel.nerdbot.discord.api.bot.Environment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.marmalade.json.JsonUtils;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation for Discord bots built on top of the NerdBot platform.
 * Subclasses are expected to provide their own configuration, listeners, features and shutdown logic.
 */
@Slf4j
public abstract class AbstractDiscordBot implements DiscordBot {

    private final Database database;
    private JDA jda;
    private DiscordBotConfig config;
    private long startTime;
    private List<BotFeature> features = Collections.emptyList();
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    protected AbstractDiscordBot() {
        this.database = Objects.requireNonNull(createDatabase(), "createDatabase() returned null");
    }

    /**
     * Creates the database instance backing this bot.
     */
    @NotNull
    protected abstract Database createDatabase();

    /**
     * Supplies the listeners that should be registered with JDA.
     */
    @NotNull
    protected abstract Collection<Object> getEventListeners();

    /**
     * Provides the features that should be started once the bot is fully ready.
     */
    @NotNull
    protected abstract Collection<? extends BotFeature> createFeatures();

    /**
     * Returns the package that should be scanned for slash commands. If {@code null} or blank, no scanning occurs.
     */
    @Nullable
    protected String getSlashCommandBasePackage() {
        return null;
    }

    /**
     * Builds the activity that should be shown for the bot. Returning {@code null} skips setting the activity.
     */
    @Nullable
    protected Activity buildActivity(@NotNull DiscordBotConfig config) {
        return null;
    }

    /**
     * Hook to customise the JDA builder before it's built.
     * Called during bot creation before JDA is initialized.
     */
    protected void customizeBuilder(@NotNull JDABuilder builder) {
    }

    /**
     * Hook called after JDA is ready and core caches are registered.
     * Use this for any initialization that requires a fully ready JDA instance.
     * Subclasses overriding this method must invoke {@code super.onReady(jda)} to ensure
     * core caches remain registered.
     */
    protected void onReady(@NotNull JDA jda) {
        jda.addEventListener(new EmojiCache(), new ChannelCache());
    }

    /**
     * Hook called during shutdown after features have stopped and JDA has shut down, but before
     * repositories are saved and the database is closed.
     */
    protected void onShutdown() {
    }

    @Override
    public final void create(String[] args) throws LoginException {
        loadConfig();

        JDABuilder builder = JDABuilder.createDefault(getBotToken())
            .setEventManager(new AnnotatedEventManager());

        Activity activity = buildActivity(config);
        if (activity != null) {
            builder.setActivity(activity);
        }

        configureMemoryUsage(builder);
        customizeBuilder(builder);

        jda = builder.build();

        try {
            jda.awaitReady();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JDA to be ready", exception);
        }

        onReady(jda);

        // Registered only now so listeners never see events before onReady has set up what they use
        Collection<Object> listeners = getEventListeners();
        if (!listeners.isEmpty()) {
            jda.addEventListener(listeners.toArray(new Object[0]));
        }

        registerSlashCommands();

        onStart();

        log.info("Bot is ready!");
        if (isReadOnly()) {
            log.info("\n!!! BOT IS LOADED IN READ-ONLY MODE !!!\n");
        }
    }

    @NotNull
    private String getBotToken() {
        String token = System.getProperty("bot.token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing bot token system property 'bot.token'");
        }
        return token;
    }

    private void registerSlashCommands() {
        String commandPackage = getSlashCommandBasePackage();
        if (commandPackage == null || commandPackage.isBlank()) {
            return;
        }

        SlashCommandManager.builder()
            .withJDA(jda)
            .scanPackage(commandPackage)
            .build();
    }

    protected void configureMemoryUsage(@NotNull JDABuilder builder) {
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);

        builder.disableCache(CacheFlag.ACTIVITY);
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.enableIntents(
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MESSAGE_REACTIONS
        );
    }

    /**
     * A feature that fails to start is logged and left out; the others still start, and only the
     * ones that started are stopped on shutdown.
     */
    @Override
    public final void onStart() {
        List<BotFeature> started = new ArrayList<>();
        for (BotFeature feature : createFeatures()) {
            if (feature == null) {
                continue;
            }

            try {
                feature.onFeatureStart();
                started.add(feature);
                log.info("Started feature {}", feature.getClass().getSimpleName());
            } catch (RuntimeException exception) {
                log.error("Feature {} failed to start and will not run", feature.getClass().getSimpleName(), exception);
            }
        }

        features = Collections.unmodifiableList(started);
        startTime = System.currentTimeMillis();

        log.info("Bot started in environment {}", Environment.getEnvironment());
    }

    /**
     * Each step runs even if an earlier one fails, so a broken feature can't stop the data from
     * being saved. JDA stops before the save so no event changes a repository mid-save. Only the
     * first call does anything; INT and TERM can both arrive.
     */
    @Override
    public final void onEnd() {
        if (!shuttingDown.compareAndSet(false, true)) {
            log.info("Shutdown already in progress");
            return;
        }

        log.info("Shutting down bot...");

        for (BotFeature feature : features) {
            runShutdownStep("stop feature " + feature.getClass().getSimpleName(), feature::onFeatureEnd);
        }
        runShutdownStep("shut down JDA", this::shutdownJda);
        runShutdownStep("run onShutdown", this::onShutdown);
        runShutdownStep("save repositories", this::saveRepositories);
        runShutdownStep("close the database connection", this::closeDatabase);
        runShutdownStep("shut down JSON utilities", JsonUtils::shutdown);

        log.info("Bot shutdown complete!");
    }

    private static void runShutdownStep(String description, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException exception) {
            log.error("Failed to {} during shutdown", description, exception);
        }
    }

    private void shutdownJda() {
        if (jda == null) {
            return;
        }

        jda.shutdown();
        try {
            if (!jda.awaitShutdown(Duration.ofSeconds(10))) {
                log.warn("JDA did not shut down within 10 seconds, forcing it");
                jda.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    private void saveRepositories() {
        Map<Class<?>, Object> repositories = database.getRepositoryManager().getRepositories();
        log.info("Saving data from {} repositories...", repositories.size());

        repositories.values().forEach(repositoryObject -> {
            Repository<?> repository = (Repository<?>) repositoryObject;
            String name = repository.getClass().getSimpleName();
            runShutdownStep("save repository " + name, () -> {
                BulkWriteResult result = repository.saveAllToDatabase();
                if (result != null && result.wasAcknowledged()) {
                    log.info("Saved {} documents for repository {} ({} inserted, {} modified, {} deleted)",
                        result.getInsertedCount() + result.getModifiedCount(), name,
                        result.getInsertedCount(), result.getModifiedCount(), result.getDeletedCount());
                } else {
                    log.info("Saved 0 documents for repository {}", name);
                }
            });
        });
    }

    private void closeDatabase() {
        MongoClient client = database.getMongoClient();
        if (client != null) {
            client.close();
        }
    }

    @Override
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    @Override
    public boolean isReadOnly() {
        return Boolean.parseBoolean(System.getProperty("bot.readOnly", "false"));
    }

    /**
     * Returns the config class type to load. Subclasses should override this to load their specific config type.
     */
    @NotNull
    protected Class<? extends DiscordBotConfig> getConfigClass() {
        return DiscordBotConfig.class;
    }

    @Override
    public void loadConfig() {
        String fileName;
        if (System.getProperty("bot.config") != null) {
            fileName = System.getProperty("bot.config");
            log.info("Found config property: {}", fileName);
        } else {
            log.info("Config property not defined, using default path");
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        File file = new File(fileName);
        log.info("Loading config file from '{}'", file.getAbsolutePath());

        try {
            Object loadedConfig = JsonUtils.jsonToObject(file, getConfigClass());
            if (!(loadedConfig instanceof DiscordBotConfig discordBotConfig)) {
                throw new IllegalStateException("Loaded config is not a DiscordBotConfig: " + file.getAbsolutePath());
            }

            config = discordBotConfig;
            log.info("Loaded config from {}", file.getAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load config from " + file.getAbsolutePath(), exception);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse config from " + file.getAbsolutePath(), exception);
        }
    }

    @Override
    public boolean writeConfig(@NotNull DiscordBotConfig newConfig) {
        String fileName;
        if (System.getProperty("bot.config") != null) {
            fileName = System.getProperty("bot.config");
            log.info("Found config property: {}", fileName);
        } else {
            log.info("Config property not defined, using default path");
            fileName = Environment.getEnvironment().name().toLowerCase() + ".config.json";
        }

        try (FileWriter writer = new FileWriter(fileName)) {
            DataSerialization.PRETTY_GSON.toJson(newConfig, writer);
            return true;
        } catch (IOException exception) {
            log.error("Could not save config file!", exception);
            return false;
        }
    }

    @Override
    public Database getDatabase() {
        return database;
    }

    @Override
    public JDA getJDA() {
        return jda;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public DiscordBotConfig getConfig() {
        return config;
    }

    @Override
    public List<BotFeature> getFeatures() {
        return features;
    }
}
