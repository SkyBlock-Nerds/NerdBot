package net.hypixel.nerdbot.app;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.app.activity.ActivityListener;
import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.app.listener.FunListener;
import net.hypixel.nerdbot.app.listener.MetricsListener;
import net.hypixel.nerdbot.app.listener.ModLogListener;
import net.hypixel.nerdbot.app.listener.PinListener;
import net.hypixel.nerdbot.app.listener.ReactionChannelListener;
import net.hypixel.nerdbot.app.listener.RoleRestrictedChannelListener;
import net.hypixel.nerdbot.app.listener.SuggestionListener;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.sentry.SentryManager;
import net.hypixel.nerdbot.app.reminder.ReminderDispatcher;
import net.hypixel.nerdbot.app.user.BirthdayScheduler;
import net.hypixel.nerdbot.discord.AbstractDiscordBot;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.AlphaProjectConfigUpdater;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * SkyBlock Nerds Discord bot implementation.
 * This class configures all listeners, features, and services specific to the SkyBlock Nerds community.
 */
@Slf4j
public class SkyBlockNerdsBot extends AbstractDiscordBot {

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
    protected @NotNull Database createDatabase() {
        String mongoUri = System.getProperty("db.mongodb.uri", "mongodb://localhost:27017/");
        return new Database(mongoUri, "skyblock_nerds");
    }

    @Override
    protected @NotNull Collection<Object> getEventListeners() {
        List<Object> listeners = new ArrayList<>(Arrays.asList(
            new ModLogListener(),
            new FeatureEventListener(),
            new ActivityListener(),
            new SuggestionListener(),
            new ReactionChannelListener(),
            new PinListener(),
            new MetricsListener(),
            new FunListener(),
            new RoleRestrictedChannelListener()
        ));

        return listeners;
    }

    @Override
    protected @NotNull Collection<? extends BotFeature> createFeatures() {
        NerdBotConfig config = getConfig();
        List<BotFeature> features = new ArrayList<>();

        if (config.getFeatures() != null) {
            log.info("Loading features from config ({} entries)", config.getFeatures().size());

            config.getFeatures().stream()
                .filter(FeatureConfig::isEnabled)
                .forEach(featureConfig -> {
                    try {
                        if (!isAllowed(featureConfig.getClassName())) {
                            log.warn("Feature class {} not permitted by class allowlist", featureConfig.getClassName());
                            return;
                        }

                        Class<?> clazz = Class.forName(featureConfig.getClassName());
                        if (!BotFeature.class.isAssignableFrom(clazz)) {
                            log.warn("Feature class {} does not implement BotFeature", featureConfig.getClassName());
                            return;
                        }

                        BotFeature feature = (BotFeature) clazz.getDeclaredConstructor().newInstance();
                        feature.setScheduleOverrides(featureConfig.getInitialDelayMs(), featureConfig.getPeriodMs());
                        features.add(feature);
                        log.info("Added feature from config: {}", featureConfig.getClassName());

                        if (feature instanceof SchedulableFeature schedulable) {
                            long defaultInitial = schedulable.defaultInitialDelayMs(config);
                            long defaultPeriod = schedulable.defaultPeriodMs(config);

                            Long overrideInitial = featureConfig.getInitialDelayMs();
                            Long overridePeriod = featureConfig.getPeriodMs();
                            long effectiveInitial = overrideInitial != null ? overrideInitial : defaultInitial;
                            long effectivePeriod = overridePeriod != null ? overridePeriod : defaultPeriod;

                            if (overrideInitial != null || overridePeriod != null) {
                                log.info(
                                    "Applying schedule override for {}: initialDelayMs={} (default {}), periodMs={} (default {})",
                                    feature.getClass().getName(), effectiveInitial, defaultInitial, effectivePeriod, defaultPeriod
                                );
                            }

                            feature.scheduleAtFixedRate(schedulable.buildTask(), defaultInitial, defaultPeriod);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to instantiate feature {}", featureConfig.getClassName(), e);
                    }
                });
        } else {
            log.info("No feature config present");
        }

        return features;
    }

    @Override
    protected String getSlashCommandBasePackage() {
        return "net.hypixel.nerdbot.app.command";
    }

    @Override
    protected Activity buildActivity(@NotNull DiscordBotConfig config) {
        return Activity.of(
            Activity.ActivityType.valueOf(config.getActivityType().name()),
            config.getActivity()
        );
    }

    @Override
    protected void onReady(@NotNull JDA jda) {
        super.onReady(jda);

        NerdBotConfig config = getConfig();

        // Update forum IDs for alpha/project channels
        AlphaProjectConfigUpdater.updateForumIds(config, true, true, jda.getForumChannels());

        // Load badges
        if (config.getBadgeConfig().getBadges() != null) {
            BadgeManager.loadBadges();
        } else {
            log.warn("No badges found in config file, so no badges will be loaded!");
        }

        // Load Discord users and schedule birthdays
        DiscordUserRepository discordUserRepository = getDatabase()
            .getRepositoryManager()
            .getRepository(DiscordUserRepository.class);

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
        PrometheusMetrics.setMetricsEnabled(false);
        SentryManager.close();
    }

    private void loadRemindersFromDatabase() {
        if (!getDatabase().isConnected()) {
            log.error("Failed to load reminders from database, database is not connected!");
            return;
        }

        log.info("Loading all reminders from database...");

        ReminderRepository reminderRepository = getDatabase()
            .getRepositoryManager()
            .getRepository(ReminderRepository.class);

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

    private static boolean isAllowed(String className) {
        return className != null && className.startsWith("net.hypixel.nerdbot.");
    }
}