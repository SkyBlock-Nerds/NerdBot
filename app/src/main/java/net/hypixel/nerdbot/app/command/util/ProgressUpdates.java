package net.hypixel.nerdbot.app.command.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Progress updates and the final update share one lock so a progress edit that's already in flight
 * can't land after the final message and overwrite it.
 */
@Slf4j
public final class ProgressUpdates {

    private final Object lock = new Object();
    private final Runnable update;
    private Timer timer;
    private boolean finished;

    ProgressUpdates(Runnable update) {
        this.update = update;
    }

    public static ProgressUpdates every(Duration period, String threadName, Runnable update) {
        ProgressUpdates progress = new ProgressUpdates(update);
        progress.timer = new Timer(threadName, true);
        progress.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                progress.tick();
            }
        }, 0, period.toMillis());
        return progress;
    }

    void tick() {
        synchronized (lock) {
            if (finished) {
                return;
            }

            try {
                update.run();
            } catch (RuntimeException exception) {
                // An exception escaping a TimerTask kills the Timer thread for good
                log.warn("Progress update failed", exception);
            }
        }
    }

    public void finish(Runnable finalUpdate) {
        if (timer != null) {
            timer.cancel();
        }

        synchronized (lock) {
            if (finished) {
                return;
            }

            finished = true;
            finalUpdate.run();
        }
    }
}
