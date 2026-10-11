package net.hypixel.nerdbot.discord.api.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.functional.ThrowingRunnable;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Something the bot starts after it's ready and stops on shutdown. Repeating work runs on a
 * scheduler the bot shares between all features, so a feature doesn't hold a thread of its own.
 */
@Slf4j
public abstract class BotFeature {

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledRun;

    private Long scheduleInitialDelayOverrideMs;
    private Long schedulePeriodOverrideMs;

    public abstract void onFeatureStart();

    public abstract void onFeatureEnd();

    public void setScheduleOverrides(Long initialDelayMs, Long periodMs) {
        this.scheduleInitialDelayOverrideMs = initialDelayMs;
        this.schedulePeriodOverrideMs = periodMs;
    }

    /** Called by the bot before {@link #onFeatureStart()}. */
    public final synchronized void useScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Runs {@code task} repeatedly on the shared scheduler. A run never overlaps the previous one,
     * and anything it throws, {@link Error}s included, is logged without stopping later runs.
     * Scheduling again replaces the earlier schedule.
     *
     * @param defaultInitialDelayMs delay before the first run, overridable through the config
     * @param defaultPeriodMs time between runs, overridable through the config
     * @throws IllegalStateException when the feature wasn't started by a bot
     */
    public synchronized void scheduleAtFixedRate(String name, ThrowingRunnable<?> task, long defaultInitialDelayMs, long defaultPeriodMs) {
        if (scheduler == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " has no scheduler; features are started by the bot");
        }

        long initialDelay = scheduleInitialDelayOverrideMs != null ? scheduleInitialDelayOverrideMs : defaultInitialDelayMs;
        long period = schedulePeriodOverrideMs != null ? schedulePeriodOverrideMs : defaultPeriodMs;

        // Logged unconditionally: a config override silently shadows the feature's default, so the
        // resolved values are the only reliable record of what the bot is actually running.
        log.info(
            "Scheduling task '{}': initialDelayMs={} ({}), periodMs={} ({})",
            name,
            initialDelay, describeSource(scheduleInitialDelayOverrideMs, defaultInitialDelayMs),
            period, describeSource(schedulePeriodOverrideMs, defaultPeriodMs)
        );

        if (scheduledRun != null) {
            scheduledRun.cancel(false);
        }

        Runnable run = ThrowingRunnable.sneaky(task);
        scheduledRun = scheduler.scheduleAtFixedRate(() -> {
            try {
                run.run();
            } catch (Throwable throwable) {
                log.error("Scheduled task '{}' failed", name, throwable);
            }
        }, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    private static String describeSource(Long override, long defaultValue) {
        return override != null ? "config override, feature default " + defaultValue : "feature default";
    }

    /** Cancels later runs; a run already going finishes. */
    public synchronized void stopScheduledTask() {
        if (scheduledRun != null) {
            scheduledRun.cancel(false);
            scheduledRun = null;
        }
    }
}
