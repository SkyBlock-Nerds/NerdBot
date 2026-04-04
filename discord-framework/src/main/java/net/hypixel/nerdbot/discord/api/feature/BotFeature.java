package net.hypixel.nerdbot.discord.api.feature;

import net.hypixel.nerdbot.marmalade.concurrent.ScheduledTask;
import net.hypixel.nerdbot.marmalade.functional.ThrowingRunnable;

import java.time.Duration;

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
        this.scheduledTask = ScheduledTask.create(name, ThrowingRunnable.sneaky(task), Duration.ofMillis(initialDelay), Duration.ofMillis(period));
        this.scheduledTask.start();
    }

    public void stopScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.stop();
        }
    }
}
