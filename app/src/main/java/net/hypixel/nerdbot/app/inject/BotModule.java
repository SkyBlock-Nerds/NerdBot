package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import net.hypixel.nerdbot.app.badge.BadgeCatalog;
import net.hypixel.nerdbot.app.badge.ConfiguredBadgeCatalog;
import net.hypixel.nerdbot.app.channel.DiscordLogChannel;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.app.metrics.MetricsServer;
import net.hypixel.nerdbot.app.metrics.PrometheusMetricsServer;
import net.hypixel.nerdbot.app.nomination.NominationSweeps;
import net.hypixel.nerdbot.app.nomination.ServiceNominationSweeps;
import net.hypixel.nerdbot.app.profile.HttpMinecraftAccounts;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.reminder.DiscordReminderSender;
import net.hypixel.nerdbot.app.reminder.ReminderSender;
import net.hypixel.nerdbot.discord.cache.MessageCache;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Module
public interface BotModule {

    int SCHEDULER_THREADS = 2;
    int BACKGROUND_WORK_THREADS = 4;
    int IMAGE_RENDERING_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());
    int MEMBER_WORK_THREADS = 4;

    @Binds
    LogChannel logChannel(DiscordLogChannel channel);

    @Binds
    ReminderSender reminderSender(DiscordReminderSender sender);

    @Binds
    BadgeCatalog badgeCatalog(ConfiguredBadgeCatalog catalog);

    @Binds
    MinecraftAccounts minecraftAccounts(HttpMinecraftAccounts accounts);

    @Binds
    ConfigStore configStore(BotConfigStore store);

    @Binds
    MetricsServer metricsServer(PrometheusMetricsServer server);

    @Binds
    NominationSweeps nominationSweeps(ServiceNominationSweeps sweeps);

    @Provides
    static Clock clock() {
        return Clock.systemUTC();
    }

    // Unscoped, so every Provider.get() reads the bot's current value
    @Provides
    static NerdBotConfig config(BotState state) {
        return state.currentConfig();
    }

    @Provides
    static Optional<PackUpdater> packUpdater(BotState state) {
        return state.currentPackUpdater();
    }

    @Provides
    static Optional<DrivePermissionService> drivePermissionService(BotState state) {
        return state.currentDrivePermissionService();
    }

    @Binds
    Executor backgroundWork(ExecutorService pool);

    /** For slow work a command starts, such as downloads, so it doesn't hold a dispatch thread. Shut down with the bot. */
    @Provides
    @Singleton
    static ExecutorService backgroundWorkPool() {
        return Executors.newFixedThreadPool(BACKGROUND_WORK_THREADS, daemonThreads("bot-work-"));
    }

    @Binds
    @ImageRendering
    Executor imageRendering(@ImageRendering ExecutorService pool);

    @Provides
    @Singleton
    @ImageRendering
    static ExecutorService imageRenderingPool() {
        return Executors.newFixedThreadPool(IMAGE_RENDERING_THREADS, daemonThreads("image-render-"));
    }

    @Binds
    @MemberWork
    Executor memberWork(@MemberWork ExecutorService pool);

    @Provides
    @Singleton
    @MemberWork
    static ExecutorService memberWorkPool() {
        return Executors.newFixedThreadPool(MEMBER_WORK_THREADS, daemonThreads("member-work-"));
    }

    @Provides
    @Singleton
    static MessageCache messageCache() {
        return new MessageCache();
    }

    @Provides
    static DriveSyncSweep driveSyncSweep() {
        return new DriveSyncSweep();
    }

    /**
     * The one scheduler for timed work, shut down with the bot. Cancelled tasks are dropped from the
     * queue straight away, so deleting a reminder due in a year doesn't leave it in memory.
     */
    @Provides
    @Singleton
    static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(SCHEDULER_THREADS, daemonThreads("bot-scheduler-"));
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static ThreadFactory daemonThreads(String namePrefix) {
        AtomicInteger threadNumber = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, namePrefix + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
