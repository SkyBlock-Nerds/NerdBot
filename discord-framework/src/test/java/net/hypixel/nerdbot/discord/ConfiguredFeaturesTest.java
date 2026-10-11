package net.hypixel.nerdbot.discord;

import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredFeaturesTest {

    public static class PlainFeature extends BotFeature {
        @Override
        public void onFeatureStart() {
        }

        @Override
        public void onFeatureEnd() {
        }
    }

    public static class ExplodingFeature extends PlainFeature {
        public ExplodingFeature() {
            throw new IllegalStateException("constructor failed");
        }
    }

    public static class NotAFeature {
    }

    static final List<String> EVENTS = Collections.synchronizedList(new ArrayList<>());
    static CountDownLatch taskRan;

    public static class ScheduledFeature extends BotFeature implements SchedulableFeature {
        private volatile boolean started;

        @Override
        public void onFeatureStart() {
            started = true;
            EVENTS.add("start");
        }

        @Override
        public void onFeatureEnd() {
            stopScheduledTask();
        }

        @Override
        public void executeTask() {
            EVENTS.add(started ? "task after start" : "task before start");
            taskRan.countDown();
        }

        @Override
        public long defaultInitialDelayMs(DiscordBotConfig config) {
            return 0;
        }

        @Override
        public long defaultPeriodMs(DiscordBotConfig config) {
            return TimeUnit.HOURS.toMillis(1);
        }
    }

    private static FeatureConfig entry(String className) {
        FeatureConfig config = new FeatureConfig();
        config.setClassName(className);
        return config;
    }

    private static String name(Class<?> type) {
        return type.getName();
    }

    @Test
    void enabledEntriesBecomeFeaturesInOrder() {
        List<BotFeature> features = AbstractDiscordBot.instantiateFeatures(List.of(
            entry(name(PlainFeature.class)),
            entry(name(ScheduledFeature.class))
        ));

        assertEquals(2, features.size());
        assertInstanceOf(PlainFeature.class, features.get(0));
        assertInstanceOf(ScheduledFeature.class, features.get(1));
    }

    @Test
    void badEntriesAreSkippedWithoutStoppingTheRest() {
        FeatureConfig disabled = entry(name(PlainFeature.class));
        disabled.setEnabled(false);

        List<BotFeature> features = AbstractDiscordBot.instantiateFeatures(Arrays.asList(
            null,
            disabled,
            entry("java.lang.String"),
            entry(null),
            entry("net.hypixel.nerdbot.DoesNotExist"),
            entry(name(NotAFeature.class)),
            entry(name(ExplodingFeature.class)),
            entry(name(PlainFeature.class))
        ));

        assertEquals(1, features.size());
        assertInstanceOf(PlainFeature.class, features.getFirst());
    }

    @Test
    void noFeatureListMeansNoFeatures() {
        assertTrue(AbstractDiscordBot.instantiateFeatures(null).isEmpty());
    }

    @Test
    void aScheduledFeaturesTaskOnlyRunsAfterItHasStarted() throws InterruptedException {
        EVENTS.clear();
        taskRan = new CountDownLatch(1);
        ScheduledFeature feature = new ScheduledFeature();
        AbstractDiscordBot bot = new AbstractDiscordBot() {
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
                return List.of(feature);
            }
        };

        bot.onStart();
        assertTrue(taskRan.await(10, TimeUnit.SECONDS));
        bot.onEnd();

        assertEquals(List.of("start", "task after start"), EVENTS);
    }
}
