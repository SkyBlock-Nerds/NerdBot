package net.hypixel.nerdbot.bot;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.activity.ActivityListener;
import net.hypixel.nerdbot.activity.ActivityPurgeFeature;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.bot.DiscordBot;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.api.feature.FeatureEventListener;
import net.hypixel.nerdbot.badge.BadgeManager;
import net.hypixel.nerdbot.config.DiscordBotConfig;
import net.hypixel.nerdbot.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.AlphaProjectConfigUpdater;
import net.hypixel.nerdbot.discord.core.AbstractDiscordBot;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        return (NerdBotConfig) ((DiscordBot) BotEnvironment.getBot()).getConfig();
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

        // Conditionally add ModMail listener if configured
        if (getConfig().getModMailConfig() != null) {
            listeners.add(new ModMailListener());
        }

        return listeners;
    }

    @Override
    protected @NotNull Collection<? extends BotFeature> createFeatures() {
        return List.of(
            new HelloGoodbyeFeature(),
            new CurateFeature(),
            new UserGrabberFeature(),
            new ProfileUpdateFeature(),
            new ActivityPurgeFeature(),
            new UserNominationFeature()
        );
    }

    @Override
    protected String getSlashCommandBasePackage() {
        return "net.hypixel.nerdbot.discord.command";
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
        startUrlWatchers();

        // Initialize member count metric
        DiscordUtils.getMainGuild().loadMembers()
            .onSuccess(members -> PrometheusMetrics.TOTAL_USERS_AMOUNT.set(members.size()))
            .onError(throwable -> log.error("Failed to load members!", throwable));

        // Enable Prometheus metrics if configured
        if (config.getMetricsConfig().isEnabled()) {
            PrometheusMetrics.setMetricsEnabled(true);
        }
    }

    @Override
    protected void onShutdown() {
        PrometheusMetrics.setMetricsEnabled(false);
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
}