package net.hypixel.nerdbot.discord.testsupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ScheduledExecutorService} whose time only moves when a test calls {@link #advance}, and
 * which runs everything on the calling thread. One-off {@code schedule} calls and
 * {@code execute} are supported; anything else throws, so a test notices when code starts relying
 * on more.
 */
public final class ManualScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {

    private final List<Task> tasks = new ArrayList<>();
    private Duration now = Duration.ZERO;
    private boolean shutdown;

    /** Moves time forward and runs every task that came due, in due order. */
    public void advance(Duration amount) {
        Duration target = now.plus(amount);
        Optional<Task> next;
        while ((next = takeNextDueBy(target)).isPresent()) {
            next.get().run();
        }
        synchronized (this) {
            now = target;
        }
    }

    /** How long until each task that is still waiting, soonest first. */
    public synchronized List<Duration> pendingDelays() {
        return tasks.stream()
            .filter(task -> !task.cancelled)
            .map(task -> task.due.minus(now))
            .sorted()
            .toList();
    }

    @Override
    public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        rejectIfShutdown();
        Task task = new Task(command, now.plus(Duration.ofNanos(Math.max(0, unit.toNanos(delay)))));
        tasks.add(task);
        return task;
    }

    @Override
    public void execute(Runnable command) {
        rejectIfShutdown();
        command.run();
    }

    /** The callable's result is dropped; {@code get()} on the returned future gives null. */
    @Override
    @SuppressWarnings("unchecked")
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        Runnable command = () -> {
            try {
                callable.call();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
        return (ScheduledFuture<V>) schedule(command, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        throw new UnsupportedOperationException("ManualScheduledExecutor doesn't support repeating tasks");
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("ManualScheduledExecutor doesn't support repeating tasks");
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        List<Runnable> waiting = tasks.stream().filter(task -> !task.cancelled).map(task -> task.command).toList();
        tasks.clear();
        return waiting;
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    private synchronized Optional<Task> takeNextDueBy(Duration target) {
        Optional<Task> next = tasks.stream()
            .filter(task -> !task.cancelled && task.due.compareTo(target) <= 0)
            .min(Comparator.comparing(task -> task.due));
        next.ifPresent(task -> {
            tasks.remove(task);
            now = task.due;
        });
        return next;
    }

    private void rejectIfShutdown() {
        if (shutdown) {
            throw new IllegalStateException("ManualScheduledExecutor has been shut down");
        }
    }

    private final class Task implements ScheduledFuture<Object> {

        private final Runnable command;
        private final Duration due;
        private volatile boolean cancelled;
        private volatile boolean done;

        private Task(Runnable command, Duration due) {
            this.command = command;
            this.due = due;
        }

        private void run() {
            command.run();
            done = true;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(due.minus(now));
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
