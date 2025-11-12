package net.hypixel.nerdbot.discord.api.feature;

import java.util.Timer;
import java.util.TimerTask;

public abstract class BotFeature {

    protected final Timer timer = new Timer();

    private Long scheduleInitialDelayOverrideMs;
    private Long schedulePeriodOverrideMs;

    public abstract void onFeatureStart();

    public abstract void onFeatureEnd();

    public void setScheduleOverrides(Long initialDelayMs, Long periodMs) {
        this.scheduleInitialDelayOverrideMs = initialDelayMs;
        this.schedulePeriodOverrideMs = periodMs;
    }

    public void scheduleAtFixedRate(TimerTask task, long defaultInitialDelayMs, long defaultPeriodMs) {
        long initialDelay = scheduleInitialDelayOverrideMs != null ? scheduleInitialDelayOverrideMs : defaultInitialDelayMs;
        long period = schedulePeriodOverrideMs != null ? schedulePeriodOverrideMs : defaultPeriodMs;
        this.timer.scheduleAtFixedRate(task, initialDelay, period);
    }
}
