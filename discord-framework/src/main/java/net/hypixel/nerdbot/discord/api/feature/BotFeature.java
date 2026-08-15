package net.hypixel.nerdbot.discord.api.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.concurrent.ScheduledTask;
import net.hypixel.nerdbot.marmalade.functional.ThrowingRunnable;

import java.time.Duration;

@Slf4j
public abstract class BotFeature {

    protected ScheduledTask scheduledTask;

    private Long scheduleInitialDelayOverrideMs;
    private Long schedulePeriodOverrideMs;

    public abstract void onFeatureStart();

    public abstract void onFeatureEnd();

    public void setScheduleOverrides(Long initialDelayMs, Long periodMs) {
        this.scheduleInitialDelayOverrideMs = initialDelayMs;
        this.schedulePeriodOverrideMs = periodMs;
    }

    /**
     * Schedules a recurring task backed by a {@link ScheduledTask}. Any checked exception thrown
     * by {@code task} is propagated sneakily and caught by the ScheduledTask error handler.
     *
     * @param name the display name for the task thread
     * @param task the work to execute on each tick
     * @param defaultInitialDelayMs delay before the first execution, overridable via config
     * @param defaultPeriodMs period between executions, overridable via config
     */
    public void scheduleAtFixedRate(String name, ThrowingRunnable<?> task, long defaultInitialDelayMs, long defaultPeriodMs) {
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

        this.scheduledTask = ScheduledTask.create(name, ThrowingRunnable.sneaky(task), Duration.ofMillis(initialDelay), Duration.ofMillis(period));
        this.scheduledTask.start();
    }

    private static String describeSource(Long override, long defaultValue) {
        return override != null ? "config override, feature default " + defaultValue : "feature default";
    }

    public void stopScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.stop();
        }
    }
}
