package net.hypixel.nerdbot.discord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.hypixel.nerdbot.core.api.bot.Environment;
import net.hypixel.nerdbot.core.api.database.Database;
import net.hypixel.nerdbot.core.api.feature.BotFeature;
import net.hypixel.nerdbot.core.api.repository.Repository;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.cache.suggestion.SuggestionCache;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.core.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base implementation for Discord bots built on top of the NerdBot platform.
 * Subclasses are expected to provide their own configuration, listeners, features and shutdown logic.
 */
@Slf4j
public abstract class AbstractDiscordBot implements DiscordBot {

    private final Database database;
    private JDA jda;
    private DiscordBotConfig config;
    private MessageCache messageCache;
    private SuggestionCache suggestionCache;
    private long startTime;
    private List<BotFeature> features = Collections.emptyList();

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
     * Hook called during shutdown before repositories are saved and database is closed.
     * Use this for cleanup tasks that need to happen before data persistence.
     */
    protected void onShutdown() {
    }

    /**
     * Factory for the message cache. Sub-classes may override to return a specialised implementation.
     */
    @NotNull
    protected MessageCache createMessageCache() {
        return new MessageCache();
    }

    /**
     * Factory for the suggestion cache. Sub-classes may override to return a specialised implementation.
     */
    @NotNull
    protected SuggestionCache createSuggestionCache() {
        return new SuggestionCache();
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

        Collection<Object> listeners = getEventListeners();
        if (!listeners.isEmpty()) {
            builder.addEventListeners(listeners.toArray(new Object[0]));
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
        messageCache = createMessageCache();
        suggestionCache = createSuggestionCache();
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

    @Override
    public final void onStart() {
        Collection<? extends BotFeature> createdFeatures = createFeatures();
        List<BotFeature> featureList = new ArrayList<>();

        createdFeatures.stream()
            .filter(Objects::nonNull)
            .forEach(featureList::add);

        featureList.forEach(feature -> {
            feature.onFeatureStart();
            log.info("Started feature {}", feature.getClass().getSimpleName());
        });

        features = Collections.unmodifiableList(featureList);
        startTime = System.currentTimeMillis();

        log.info("Bot started in environment {}", Environment.getEnvironment());
    }

    @Override
    public final void onEnd() {
        log.info("Shutting down bot...");

        features.forEach(BotFeature::onFeatureEnd);

        onShutdown();

        try {
            Map<Class<?>, Object> repositories = database.getRepositoryManager().getRepositories();
            log.info("Saving data from {} repositories...", repositories.size());

            repositories.forEach((type, repositoryObject) -> {
                Repository<?> repository = (Repository<?>) repositoryObject;
                repository.saveAllToDatabaseAsync()
                    .thenAccept(result -> {
                        if (result != null && result.wasAcknowledged()) {
                            int total = result.getInsertedCount() + result.getModifiedCount();
                            log.info(
                                "Saved {} documents for repository {} ({} inserted, {} modified, {} deleted)",
                                total,
                                repository.getClass().getSimpleName(),
                                result.getInsertedCount(),
                                result.getModifiedCount(),
                                result.getDeletedCount()
                            );
                        } else {
                            log.info("Saved 0 documents for repository {}", repository.getClass().getSimpleName());
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Failed to save documents for repository {}", repository.getClass().getSimpleName(), throwable);
                        return null;
                    })
                    .join();
            });
        } catch (Exception exception) {
            log.error("Error while saving data: {}", exception.getMessage(), exception);
        } finally {
            database.getMongoClient().close();
        }

        JsonUtils.shutdown();

        log.info("Bot shutdown complete!");
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

        JsonUtils.jsonToObjectAsync(file, getConfigClass())
            .thenAccept(loadedConfig -> {
                config = (DiscordBotConfig) loadedConfig;
                log.info("Loaded config from {}", file.getAbsolutePath());
            })
            .exceptionally(throwable -> {
                log.error("Failed to load config from {}", file.getAbsolutePath(), throwable);
                System.exit(-1);
                return null;
            })
            .join();
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

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (FileWriter writer = new FileWriter(fileName)) {
            gson.toJson(newConfig, writer);
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
    public MessageCache getMessageCache() {
        return messageCache;
    }

    @Override
    public SuggestionCache getSuggestionCache() {
        return suggestionCache;
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