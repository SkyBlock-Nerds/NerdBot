package net.hypixel.nerdbot.discord;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.SlashCommandManager;
import net.aerh.slashcommands.SlashCommandManagerBuilder;
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
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import net.hypixel.nerdbot.marmalade.json.JsonUtils;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.login.LoginException;

/**
 * Base implementation for Discord bots built on top of the NerdBot platform.
 * Subclasses are expected to provide their own configuration, listeners, features and shutdown logic.
 */
@Slf4j
public abstract class AbstractDiscordBot implements DiscordBot {

    private final Database database;
    private JDA jda;
    private volatile DiscordBotConfig config;
    private long startTime;
    private List<BotFeature> features = Collections.emptyList();
    private volatile ScheduledExecutorService featureScheduler;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    static final String FEATURE_PACKAGE_PREFIX = "net.hypixel.nerdbot.";
    static final int FEATURE_THREADS = 4;
    static final Duration FEATURE_RUN_GRACE = Duration.ofSeconds(30);

    /** Turns a {@code features[].className} entry into a feature, or empty when it isn't one. */
    @FunctionalInterface
    public interface FeatureFactory {
        Optional<BotFeature> create(String className);
    }

    protected AbstractDiscordBot() {
        this.database = Objects.requireNonNull(createDatabase(), "createDatabase() returned null");
        // Read-only copies run against live data, so nothing they cache may be written back
        database.getRepositoryManager().setReadOnly(isReadOnly());
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
     * Only for command classes with a no-arg constructor; bots that build their commands themselves
     * return them from {@link #createSlashCommands()} instead.
     */
    @Nullable
    protected String getSlashCommandBasePackage() {
        return null;
    }

    /**
     * Command instances to register, for bots that construct their commands with their
     * dependencies. Called after {@link #onReady(JDA)}. A bot uses either this or
     * {@link #getSlashCommandBasePackage()}, not both.
     */
    @NotNull
    protected Collection<Object> createSlashCommands() {
        return List.of();
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
        SlashCommandManagerBuilder builder = SlashCommandManager.builder().withJDA(jda);
        if (addCommandSources(builder, createSlashCommands(), getSlashCommandBasePackage())) {
            builder.build();
        }
    }

    /**
     * Adds the bot's commands to {@code builder}, returning false when it has none.
     *
     * @throws IllegalStateException when the bot supplies both instances and a package, since the
     *                               scan would register a second copy of every command
     */
    static boolean addCommandSources(SlashCommandManagerBuilder builder, Collection<Object> commands, @Nullable String commandPackage) {
        boolean hasPackage = commandPackage != null && !commandPackage.isBlank();
        if (!commands.isEmpty() && hasPackage) {
            throw new IllegalStateException("Bot returns command instances and a command package; use one or the other");
        }

        if (!commands.isEmpty()) {
            builder.registerCommands(commands.toArray());
            return true;
        }

        if (hasPackage) {
            builder.scanPackage(commandPackage);
            return true;
        }

        return false;
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
     * ones that started are stopped on shutdown. A {@link SchedulableFeature}'s task is scheduled
     * after its {@code onFeatureStart}, so the task never runs before the feature is set up. Every
     * feature shares one scheduler.
     */
    @Override
    public final void onStart() {
        featureScheduler = newFeatureScheduler();
        List<BotFeature> started = new ArrayList<>();

        for (BotFeature feature : createFeatures()) {
            if (feature == null) {
                continue;
            }

            feature.useScheduler(featureScheduler);
            try {
                feature.onFeatureStart();
            } catch (RuntimeException exception) {
                log.error("Feature {} failed to start and will not run", feature.getClass().getSimpleName(), exception);
                continue;
            }

            try {
                scheduleIfSchedulable(feature);
            } catch (RuntimeException exception) {
                log.error("Feature {} could not be scheduled, stopping it", feature.getClass().getSimpleName(), exception);
                runShutdownStep("stop feature " + feature.getClass().getSimpleName(), feature::onFeatureEnd);
                continue;
            }

            started.add(feature);
            log.info("Started feature {}", feature.getClass().getSimpleName());
        }

        features = Collections.unmodifiableList(started);
        startTime = System.currentTimeMillis();

        log.info("Bot started in environment {}", Environment.getEnvironment());
    }

    private static ScheduledExecutorService newFeatureScheduler() {
        AtomicInteger threads = new AtomicInteger();
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(FEATURE_THREADS, task -> {
            Thread thread = new Thread(task, "feature-scheduler-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private void scheduleIfSchedulable(BotFeature feature) {
        if (feature instanceof SchedulableFeature schedulable) {
            feature.scheduleAtFixedRate(
                feature.getClass().getSimpleName() + "-task",
                schedulable::executeTask,
                schedulable.defaultInitialDelayMs(config),
                schedulable.defaultPeriodMs(config)
            );
        }
    }

    /**
     * Instantiates the features listed under {@code features} in the config. Bots call this from
     * {@link #createFeatures()} and add any always-on features of their own.
     */
    protected final List<BotFeature> createConfiguredFeatures() {
        return createConfiguredFeatures(AbstractDiscordBot::constructByReflection);
    }

    /** As {@link #createConfiguredFeatures()}, with the bot building each feature itself, for example through injection. */
    protected final List<BotFeature> createConfiguredFeatures(FeatureFactory factory) {
        return instantiateFeatures(config == null ? null : config.getFeatures(), factory);
    }

    /**
     * Enabled entries become features with their schedule overrides applied. An entry outside
     * {@value #FEATURE_PACKAGE_PREFIX}, that isn't a {@link BotFeature}, or that can't be
     * constructed is logged and skipped rather than stopping the rest.
     */
    static List<BotFeature> instantiateFeatures(@Nullable List<FeatureConfig> featureConfigs) {
        return instantiateFeatures(featureConfigs, AbstractDiscordBot::constructByReflection);
    }

    static List<BotFeature> instantiateFeatures(@Nullable List<FeatureConfig> featureConfigs, FeatureFactory factory) {
        if (featureConfigs == null) {
            log.info("No feature config present");
            return List.of();
        }

        log.info("Loading features from config ({} entries)", featureConfigs.size());
        List<BotFeature> features = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (FeatureConfig featureConfig : featureConfigs) {
            if (featureConfig == null || !featureConfig.isEnabled()) {
                continue;
            }

            String className = featureConfig.getClassName();
            if (className == null || !className.startsWith(FEATURE_PACKAGE_PREFIX)) {
                log.warn("Feature class {} not permitted by class allowlist", className);
                continue;
            }
            if (!listed.add(className)) {
                log.warn("Feature class {} is listed more than once; starting it once", className);
                continue;
            }

            try {
                Optional<BotFeature> feature = factory.create(className);
                if (feature.isEmpty()) {
                    log.warn("Feature class {} is not a feature this bot knows", className);
                    continue;
                }

                feature.get().setScheduleOverrides(featureConfig.getInitialDelayMs(), featureConfig.getPeriodMs());
                features.add(feature.get());
                log.info("Added feature from config: {}", className);
            } catch (RuntimeException exception) {
                log.warn("Failed to instantiate feature {}", className, exception);
            }
        }
        return features;
    }

    private static Optional<BotFeature> constructByReflection(String className) {
        try {
            Class<?> type = Class.forName(className);
            if (!BotFeature.class.isAssignableFrom(type)) {
                return Optional.empty();
            }
            return Optional.of((BotFeature) type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException exception) {
            log.warn("Failed to construct feature {}", className, exception);
            return Optional.empty();
        }
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
        runShutdownStep("wait for running feature tasks", this::stopFeatureScheduler);
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

    /** Lets runs already going finish before anything they use is shut down or saved. */
    private void stopFeatureScheduler() {
        ScheduledExecutorService scheduler = featureScheduler;
        if (scheduler == null) {
            return;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(FEATURE_RUN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("Feature tasks were still running after {} seconds, interrupting them", FEATURE_RUN_GRACE.toSeconds());
                scheduler.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
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
        if (isReadOnly()) {
            log.info("Not saving repositories; the bot is in read-only mode");
            return;
        }

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

    /** The config file: the {@code bot.config} system property, or {@code <environment>.config.json}. */
    public Path configFile() {
        String property = System.getProperty("bot.config");
        return Path.of(property != null ? property : Environment.getEnvironment().name().toLowerCase(Locale.ROOT) + ".config.json");
    }

    /** Replaces the live config with the file's. When the file can't be read or parsed the live config is left alone. */
    @Override
    public synchronized void loadConfig() {
        File file = configFile().toFile();
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
        try {
            saveConfig(newConfig);
            return true;
        } catch (IOException exception) {
            log.error("Could not save config file!", exception);
            return false;
        }
    }

    /**
     * Writes {@code newConfig} to the config file and makes it the live config. The file is written
     * next to the old one and moved over it, so a crash part way through can't leave half a config.
     */
    public synchronized void saveConfig(@NotNull DiscordBotConfig newConfig) throws IOException {
        Path file = configFile().toAbsolutePath();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, DataSerialization.PRETTY_GSON.toJson(newConfig), StandardCharsets.UTF_8);

        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }

        config = newConfig;
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
