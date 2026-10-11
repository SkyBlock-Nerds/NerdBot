package net.hypixel.nerdbot.app;

import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.PackGlyphIndex;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.app.config.AlphaProjectConfigUpdater;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.feature.ResourcePackUpdateFeature;
import net.hypixel.nerdbot.app.generation.pack.PackConfigMapper;
import net.hypixel.nerdbot.app.generation.pack.update.HttpHypixelPackApiClient;
import net.hypixel.nerdbot.app.generation.pack.update.PackBootResolver;
import net.hypixel.nerdbot.app.generation.pack.update.PackCacheStore;
import net.hypixel.nerdbot.app.generation.pack.update.PackDownloader;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdateNotifier;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.app.inject.BotComponent;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.inject.DaggerBotComponent;
import net.hypixel.nerdbot.app.inject.StorageModule;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.sentry.SentryManager;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.discord.AbstractDiscordBot;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.cache.MessageCache;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SkyBlock Nerds Discord bot implementation.
 * This class configures all listeners, features, and services specific to the SkyBlock Nerds community.
 */
@Slf4j
public class SkyBlockNerdsBot extends AbstractDiscordBot implements BotState {

    private MessageCache messageCache;

    /**
     * Constructed eagerly (dependency-free) so it is never null on the interaction threads that
     * read it; the actual packs are loaded later in {@link #onReady(JDA)} once config is available.
     */
    private final ResourcePackService resourcePackService = new ResourcePackService(PackRepository.global());

    private volatile DrivePermissionService drivePermissionService;

    private volatile BotComponent component;

    /** Null when no pack opts into auto-update or the cache directory is unusable. */
    private volatile PackUpdater packUpdater;


    /**
     * Static helper to get the MessageCache from the current bot instance.
     */
    public static MessageCache messageCache() {
        return ((SkyBlockNerdsBot) DiscordBotEnvironment.getBot()).messageCache;
    }

    /**
     * Static helper for the Drive permission service. Empty whenever the feature
     * is disabled in config or its secrets are absent; callers no-op in that case.
     */
    public static java.util.Optional<DrivePermissionService> drivePermissionService() {
        return java.util.Optional.ofNullable(((SkyBlockNerdsBot) DiscordBotEnvironment.getBot()).drivePermissionService);
    }

    /**
     * Static helper for the resource pack updater. Empty when no pack opts into auto-update or
     * the pack cache directory could not be opened.
     */
    public static java.util.Optional<PackUpdater> packUpdater() {
        return java.util.Optional.ofNullable(((SkyBlockNerdsBot) DiscordBotEnvironment.getBot()).packUpdater);
    }

    /**
     * Static helper to get the SuggestionCache from the current bot instance.
     */
    public static SuggestionCache suggestionCache() {
        return ((SkyBlockNerdsBot) DiscordBotEnvironment.getBot()).component.suggestionCache();
    }

    @Override
    protected @NotNull Class<? extends DiscordBotConfig> getConfigClass() {
        return NerdBotConfig.class;
    }

    @Override
    public NerdBotConfig getConfig() {
        return (NerdBotConfig) super.getConfig();
    }

    /**
     * Static helper to get the NerdBotConfig from the current bot instance.
     * Provides clean, type-safe access to NerdBot-specific configuration throughout the bot module.
     *
     * @return The NerdBotConfig for the running bot instance
     */
    public static NerdBotConfig config() {
        return (NerdBotConfig) DiscordBotEnvironment.getBot().getConfig();
    }

    @Override
    public NerdBotConfig currentConfig() {
        return getConfig();
    }

    @Override
    public Optional<PackUpdater> currentPackUpdater() {
        return Optional.ofNullable(packUpdater);
    }

    @Override
    public Optional<DrivePermissionService> currentDrivePermissionService() {
        return Optional.ofNullable(drivePermissionService);
    }

    @Override
    protected @NotNull Database createDatabase() {
        String mongoUri = System.getProperty("db.mongodb.uri", "mongodb://localhost:27017/");
        return new Database(mongoUri, StorageModule.DATABASE_NAME);
    }

    @Override
    protected @NotNull Collection<Object> getEventListeners() {
        return List.copyOf(component.eventListeners());
    }

    @Override
    protected @NotNull Collection<? extends BotFeature> createFeatures() {
        Map<Class<?>, Provider<BotFeature>> available = component.features();
        return combineFeatures(createConfiguredFeatures(className -> findFeature(available, className)), component.alwaysOnFeatures());
    }

    static Optional<BotFeature> findFeature(Map<Class<?>, Provider<BotFeature>> available, String className) {
        return available.entrySet().stream()
            .filter(entry -> entry.getKey().getName().equals(className))
            .findFirst()
            .map(entry -> entry.getValue().get());
    }

    /** The configured features, then the always-on ones; a config entry for an always-on feature is ignored. */
    static List<BotFeature> combineFeatures(List<BotFeature> configured, List<BotFeature> alwaysOn) {
        Set<Class<?>> alwaysOnTypes = alwaysOn.stream().map(Object::getClass).collect(Collectors.toSet());

        List<BotFeature> features = new ArrayList<>();
        for (BotFeature feature : configured) {
            if (alwaysOnTypes.contains(feature.getClass())) {
                log.warn("{} always runs, so its config entry is ignored", feature.getClass().getSimpleName());
            } else {
                features.add(feature);
            }
        }
        features.addAll(alwaysOn);
        return features;
    }

    @Override
    protected @NotNull Collection<Object> createSlashCommands() {
        return List.copyOf(component.slashCommands());
    }

    @Override
    protected Activity buildActivity(@NotNull DiscordBotConfig config) {
        return Activity.of(config.getActivityType().toJda(), config.getActivity());
    }

    @Override
    protected void onReady(@NotNull JDA jda) {
        super.onReady(jda);

        component = DaggerBotComponent.factory().create(jda, getDatabase(), this, resourcePackService);

        messageCache = new MessageCache();
        component.suggestionCache().start();

        NerdBotConfig config = getConfig();

        // Register resource packs with the image generator
        GeneratorConfig.ResourcePackConfig packConfig = config.getGeneratorConfig().getResourcePacks();
        boolean packsRegistered = false;
        try {
            GeneratorConfig.AutoUpdateSettings settings = autoUpdateSettings(packConfig);
            PackCacheStore packCacheStore = openPackCacheStore(packConfig, settings);
            Map<String, Path> cachedPackPaths = packCacheStore == null ? Map.of() : PackBootResolver.cachedPaths(packConfig, packCacheStore);
            resourcePackService.registerConfiguredPacks(PackConfigMapper.toRegistrationConfig(packConfig, cachedPackPaths));
            packsRegistered = true;

            if (packCacheStore != null) {
                PackBootResolver.repairUnregistered(packConfig, cachedPackPaths, resourcePackService, packCacheStore);
                packUpdater = new PackUpdater(resourcePackService, new HttpHypixelPackApiClient(settings), new PackDownloader(settings),
                    packCacheStore, settings, new PackUpdateNotifier(), Clock.systemUTC());
                warnIfUpdateFeatureMissing(config);
            }
        } catch (RuntimeException e) {
            // A bad auto-update setting must never stop the bot from booting on the configured packs
            log.error("Resource pack auto-update is off: startup failed", e);
            packUpdater = null;
            if (!packsRegistered) {
                resourcePackService.registerConfiguredPacks(PackConfigMapper.toRegistrationConfig(packConfig));
            }
        }

        // Empty when the feature is disabled in config or its secrets are absent; commands/listener no-op in that case
        drivePermissionService = DrivePermissionService.fromSystemProperties(config.getGoogleDriveConfig()).orElse(null);

        // Validate glyph override data (icons.json/stats.json, including any external override
        // files) now instead of on the first /gen parse that needs it
        try {
            PackGlyphIndex.fromRegistries();
        } catch (IllegalStateException e) {
            log.error("Glyph override data failed validation; /gen parse will reject NBT input until the data is fixed", e);
        }

        // Update forum IDs for alpha/project channels
        AlphaProjectConfigUpdater.updateForumIds(config, true, true, jda.getForumChannels());

        // Load badges
        if (config.getBadgeConfig().getBadges() != null) {
            BadgeManager.loadBadges();
        } else {
            log.warn("No badges found in config file, so no badges will be loaded!");
        }

        // Load Discord users into the cache
        DiscordUserRepository discordUserRepository = getDatabase()
            .getRepositoryManager()
            .getRepository(DiscordUserRepository.class);

        if (discordUserRepository != null) {
            discordUserRepository.loadAllDocumentsIntoCacheAsync()
                .thenRun(() -> log.info("Successfully loaded all Discord users into cache"))
                .exceptionally(throwable -> {
                    log.error("Failed to load Discord users into cache", throwable);
                    return null;
                });
        }

        restoreReminders();

        // Initialize member count metric
        DiscordUtils.getMainGuild().loadMembers()
            .onSuccess(members -> PrometheusMetrics.TOTAL_USERS_AMOUNT.set(members.size()))
            .onError(throwable -> log.error("Failed to load members!", throwable));

        // Enable Prometheus metrics if configured
        if (config.getMetricsConfig().isEnabled()) {
            PrometheusMetrics.setMetricsEnabled(true);
        }

        // Configure Sentry environment (auto-initialized via sentry.properties)
        SentryManager.configureEnvironment();
    }

    @Override
    protected void onShutdown() {
        if (component != null) {
            component.scheduler().shutdownNow();
            component.backgroundWorkPool().shutdownNow();
            component.imageRenderingPool().shutdownNow();
        }
        PrometheusMetrics.setMetricsEnabled(false);
        SentryManager.close();
    }

    private void restoreReminders() {
        if (!getDatabase().isConnected()) {
            log.error("Failed to load reminders from database, database is not connected!");
            return;
        }

        component.reminderScheduler().restoreAll().exceptionally(throwable -> {
            log.error("Failed to load reminders from database", throwable);
            return null;
        });
    }

    /**
     * Opens the pack cache when at least one pack opts into auto-update. Returns null (auto-update
     * off for this run) when none does or the directory cannot be created.
     */
    private static PackCacheStore openPackCacheStore(GeneratorConfig.ResourcePackConfig packConfig, GeneratorConfig.AutoUpdateSettings settings) {
        // The one boot-time call that reports why packs are excluded; later calls log at debug
        List<GeneratorConfig.PackDefinition> autoUpdated = PackUpdater.enabledDefinitions(packConfig, true);
        if (autoUpdated.isEmpty()) {
            return null;
        }

        if (settings.getCacheDir() == null || settings.getCacheDir().isBlank()) {
            log.error("Resource pack auto-update is off: generatorConfig.resourcePacks.autoUpdate.cacheDir is empty");
            return null;
        }

        try {
            Path cacheDir = Path.of(settings.getCacheDir()).toAbsolutePath().normalize();
            for (GeneratorConfig.PackDefinition definition : autoUpdated) {
                try {
                    Path configured = Path.of(definition.getPath()).toAbsolutePath().normalize();
                    if (configured.startsWith(cacheDir)) {
                        log.warn("Pack '{}' is configured from inside the pack cache folder '{}'; cache cleanup may delete it, move it elsewhere",
                            definition.getId(), cacheDir);
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not check whether pack '{}' is configured from inside the pack cache folder", definition.getId(), e);
                }
            }
            return PackCacheStore.open(cacheDir, settings.getMaxRejectedHashes());
        } catch (RuntimeException e) {
            log.error("Resource pack auto-update is off: could not open the pack cache at '{}'", settings.getCacheDir(), e);
            return null;
        }
    }

    /** Warns when auto-update is set up but nothing schedules the checks, leaving only /pack check. */
    private static void warnIfUpdateFeatureMissing(NerdBotConfig config) {
        boolean scheduled = config.getFeatures() != null && config.getFeatures().stream()
            .anyMatch(feature -> feature.isEnabled() && ResourcePackUpdateFeature.class.getName().equals(feature.getClassName()));
        if (!scheduled) {
            log.warn("Resource pack auto-update is configured but ResourcePackUpdateFeature is not in the features list, so updates only happen through /pack check");
        }
    }

    /** The shared auto-update settings, or the defaults when the config sets "autoUpdate": null. */
    private static GeneratorConfig.AutoUpdateSettings autoUpdateSettings(GeneratorConfig.ResourcePackConfig packConfig) {
        GeneratorConfig.AutoUpdateSettings settings = packConfig == null ? null : packConfig.getAutoUpdate();
        return settings == null ? new GeneratorConfig.AutoUpdateSettings() : settings;
    }

}