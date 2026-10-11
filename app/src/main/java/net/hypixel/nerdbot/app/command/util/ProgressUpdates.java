package net.hypixel.nerdbot.app.command.util;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.InteractionHook;

import java.time.Clock;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

/**
 * Progress edits for a long-running command. Progress updates and the final update share one lock
 * so a progress edit that's already in flight can't land after the final message and overwrite it.
 *
 * <p>Two ways to drive it: {@link #every} polls an update on a timer, and {@link #throttled}
 * takes statuses as the work reports them and edits at most once per interval, dropping the ones in
 * between, so a chatty export doesn't hit Discord's rate limits.
 */
@Slf4j
public final class ProgressUpdates {

    private final Object lock = new Object();
    private final Runnable update;
    private final Consumer<String> edit;
    private final Duration minInterval;
    private final Clock clock;
    private Timer timer;
    private boolean finished;
    private Long lastEditMillis;

    ProgressUpdates(Runnable update) {
        this(update, null, Duration.ZERO, null);
    }

    private ProgressUpdates(Runnable update, Consumer<String> edit, Duration minInterval, Clock clock) {
        this.update = update;
        this.edit = edit;
        this.minInterval = minInterval;
        this.clock = clock;
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

    /** Statuses passed to {@link #report} are edited in at most once per {@code minInterval}; the first always is. */
    public static ProgressUpdates throttled(Duration minInterval, Clock clock, Consumer<String> edit) {
        return new ProgressUpdates(() -> {
        }, edit, minInterval, clock);
    }

    /** Edits a deferred reply with the latest status at most once per {@code minInterval}, while the hook is still usable. */
    public static ProgressUpdates throttledReply(InteractionHook hook, Duration minInterval, Clock clock) {
        return throttled(minInterval, clock, status -> {
            if (!hook.isExpired()) {
                hook.editOriginal(status).queue(null, failure -> log.debug("Failed to send progress update: {}", failure.getMessage()));
            }
        });
    }

    /** A callback for work that reports progress: every status is logged, and reported as {@link #report} allows. */
    public Consumer<String> loggingReporter() {
        return status -> {
            log.info(status);
            report(status);
        };
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

    public void report(String status) {
        if (edit == null) {
            throw new IllegalStateException("report() is for progress made with throttled()");
        }

        synchronized (lock) {
            long now = clock.millis();
            if (finished || (lastEditMillis != null && now - lastEditMillis < minInterval.toMillis())) {
                return;
            }

            lastEditMillis = now;
            try {
                edit.accept(status);
            } catch (RuntimeException exception) {
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
