package net.hypixel.skyblocknerds.api.feature;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public abstract class Feature extends Timer {

    private final int timeLength;
    private final TimeUnit timeUnit;

    /**
     * Creates a new feature that will run every timeLength in timeUnit
     *
     * @param timeLength The length of time between each run
     * @param timeUnit   The {@link TimeUnit} of timeLength
     */
    public Feature(int timeLength, TimeUnit timeUnit) {
        this.timeLength = timeLength;
        this.timeUnit = timeUnit;
    }

    public Feature() {
        this(0, null);
    }

    /**
     * Schedules the feature to run every timeLength in {@link TimeUnit}
     */
    public void schedule() {
        if (timeLength == 0 || timeUnit == null) {
            // Not all features will be recurring
            onFeatureStart();
            return;
        }

        this.schedule(new TimerTask() {
            @Override
            public void run() {
                onFeatureStart();
            }
        }, 0, timeUnit.toMillis(timeLength));
    }

    /**
     * Called when the feature is started
     */
    public abstract void onFeatureStart();

    /**
     * Called when the feature is ended
     */
    public abstract void onFeatureEnd();
}
