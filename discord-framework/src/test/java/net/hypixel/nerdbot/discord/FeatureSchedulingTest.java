package net.hypixel.nerdbot.discord;

import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSchedulingTest {

    private final List<BotFeature> features = new ArrayList<>();
    private final AbstractDiscordBot bot = new AbstractDiscordBot() {
        @Override
        protected @NotNull Database createDatabase() {
            return new Database(null, null);
        }

        @Override
        protected @NotNull Collection<Object> getEventListeners() {
            return List.of();
        }

        @Override
        protected @NotNull Collection<? extends BotFeature> createFeatures() {
            return features;
        }
    };

    @AfterEach
    void stopBot() {
        bot.onEnd();
    }

    /** Runs {@code task} every few milliseconds once started. */
    private static class RepeatingFeature extends BotFeature {
        private final Runnable task;
        private final long periodMs;

        RepeatingFeature(Runnable task, long periodMs) {
            this.task = task;
            this.periodMs = periodMs;
        }

        @Override
        public void onFeatureStart() {
            scheduleAtFixedRate("repeating", task::run, 0, periodMs);
        }

        @Override
        public void onFeatureEnd() {
            stopScheduledTask();
        }
    }

    @Test
    void featuresShareOneSchedulerInsteadOfAThreadEach() throws InterruptedException {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        CountDownLatch ran = new CountDownLatch(20);
        for (int i = 0; i < 10; i++) {
            features.add(new RepeatingFeature(() -> {
                threads.add(Thread.currentThread().getName());
                ran.countDown();
            }, 5));
        }

        bot.onStart();

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertTrue(threads.size() <= AbstractDiscordBot.FEATURE_THREADS, "threads used: " + threads);
        assertTrue(threads.stream().allMatch(name -> name.startsWith("feature-scheduler-")), threads.toString());
    }

    @Test
    void anErrorInATaskDoesNotStopLaterRuns() throws InterruptedException {
        CountDownLatch runs = new CountDownLatch(3);
        features.add(new RepeatingFeature(() -> {
            runs.countDown();
            throw new NoClassDefFoundError("deliberate");
        }, 5));

        bot.onStart();

        assertTrue(runs.await(5, TimeUnit.SECONDS));
    }

    @Test
    void aRunNeverOverlapsTheOneBefore() throws InterruptedException {
        AtomicInteger running = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean();
        CountDownLatch runs = new CountDownLatch(4);
        features.add(new RepeatingFeature(() -> {
            if (running.incrementAndGet() > 1) {
                overlapped.set(true);
            }
            sleep(30);
            running.decrementAndGet();
            runs.countDown();
        }, 1));

        bot.onStart();

        assertTrue(runs.await(5, TimeUnit.SECONDS));
        assertFalse(overlapped.get());
    }

    @Test
    void shutdownWaitsForARunInProgress() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        features.add(new RepeatingFeature(() -> {
            started.countDown();
            sleep(300);
            finished.set(true);
        }, 60_000));
        bot.onStart();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        bot.onEnd();

        assertTrue(finished.get(), "shutdown returned before the run finished");
    }

    @Test
    void schedulingAgainReplacesTheEarlierSchedule() throws InterruptedException {
        AtomicInteger first = new AtomicInteger();
        CountDownLatch second = new CountDownLatch(3);
        features.add(new BotFeature() {
            @Override
            public void onFeatureStart() {
                scheduleAtFixedRate("first", first::incrementAndGet, 60_000, 60_000);
                scheduleAtFixedRate("second", second::countDown, 0, 5);
            }

            @Override
            public void onFeatureEnd() {
                stopScheduledTask();
            }
        });

        bot.onStart();

        assertTrue(second.await(5, TimeUnit.SECONDS));
        assertEquals(0, first.get());
    }

    @Test
    void aFeatureNotStartedByABotCantSchedule() {
        RepeatingFeature feature = new RepeatingFeature(() -> {
        }, 5);

        assertThrows(IllegalStateException.class, feature::onFeatureStart);
    }

    @Test
    void anInvalidPeriodLeavesTheFeatureOut() {
        features.add(new RepeatingFeature(() -> {
        }, 0));

        bot.onStart();

        assertTrue(bot.getFeatures().isEmpty());
    }

    @Test
    void configuredFeaturesCanBeBuiltByTheBot() {
        PlainFeature built = new PlainFeature();
        FeatureConfig entry = new FeatureConfig();
        entry.setClassName("net.hypixel.nerdbot.Example");
        entry.setPeriodMs(1_000L);

        List<BotFeature> created = AbstractDiscordBot.instantiateFeatures(List.of(entry), name -> Optional.of(built));

        assertEquals(List.of(built), created);
    }

    @Test
    void aClassListedTwiceInTheConfigStartsOnce() {
        FeatureConfig first = new FeatureConfig();
        first.setClassName("net.hypixel.nerdbot.Example");
        FeatureConfig again = new FeatureConfig();
        again.setClassName("net.hypixel.nerdbot.Example");
        AtomicInteger built = new AtomicInteger();

        List<BotFeature> created = AbstractDiscordBot.instantiateFeatures(List.of(first, again), name -> {
            built.incrementAndGet();
            return Optional.of(new PlainFeature());
        });

        assertEquals(1, created.size());
        assertEquals(1, built.get());
    }

    @Test
    void anUnknownConfiguredClassIsSkipped() {
        FeatureConfig entry = new FeatureConfig();
        entry.setClassName("net.hypixel.nerdbot.Unknown");

        assertTrue(AbstractDiscordBot.instantiateFeatures(List.of(entry), name -> Optional.empty()).isEmpty());
    }

    private static class PlainFeature extends BotFeature {
        @Override
        public void onFeatureStart() {
        }

        @Override
        public void onFeatureEnd() {
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
