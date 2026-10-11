package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Singleton;
import net.hypixel.nerdbot.app.channel.DiscordLogChannel;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.reminder.DiscordReminderSender;
import net.hypixel.nerdbot.app.reminder.ReminderSender;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Module
public interface BotModule {

    int SCHEDULER_THREADS = 2;

    @Binds
    LogChannel logChannel(DiscordLogChannel channel);

    @Binds
    ReminderSender reminderSender(DiscordReminderSender sender);

    @Provides
    static Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The one scheduler for timed work, shut down with the bot. Cancelled tasks are dropped from the
     * queue straight away, so deleting a reminder due in a year doesn't leave it in memory.
     */
    @Provides
    @Singleton
    static ScheduledExecutorService scheduler() {
        AtomicInteger threadNumber = new AtomicInteger();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(SCHEDULER_THREADS, task -> {
            Thread thread = new Thread(task, "bot-scheduler-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
