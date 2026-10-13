package net.hypixel.nerdbot.discord;

import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotLifecycleTest {

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());

    private final class RecordingFeature extends BotFeature {
        private final String name;
        private final boolean failToStart;
        private final boolean failToStop;

        RecordingFeature(String name, boolean failToStart, boolean failToStop) {
            this.name = name;
            this.failToStart = failToStart;
            this.failToStop = failToStop;
        }

        @Override
        public void onFeatureStart() {
            if (failToStart) {
                throw new IllegalStateException(name + " could not start");
            }
            events.add("start " + name);
        }

        @Override
        public void onFeatureEnd() {
            if (failToStop) {
                throw new IllegalStateException(name + " could not stop");
            }
            events.add("stop " + name);
        }
    }

    @Test
    void aReadOnlyBotsRepositoriesDontWrite() {
        String previous = System.getProperty("bot.readOnly");
        System.setProperty("bot.readOnly", "true");
        try {
            assertTrue(new TestBot(false).getDatabase().getRepositoryManager().isReadOnly());
        } finally {
            if (previous == null) {
                System.clearProperty("bot.readOnly");
            } else {
                System.setProperty("bot.readOnly", previous);
            }
        }

        assertFalse(new TestBot(false).getDatabase().getRepositoryManager().isReadOnly());
    }

    private RecordingFeature feature(String name) {
        return new RecordingFeature(name, false, false);
    }

    private final class TestBot extends AbstractDiscordBot {
        private final List<BotFeature> features;
        private final boolean failOnShutdown;

        TestBot(boolean failOnShutdown, BotFeature... features) {
            this.features = new ArrayList<>(List.of(features));
            this.failOnShutdown = failOnShutdown;
        }

        @Override
        protected @NotNull Database createDatabase() {
            // No URI means no connection: the bot runs as it does without a database configured
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

        @Override
        protected void onShutdown() {
            events.add("onShutdown");
            if (failOnShutdown) {
                throw new IllegalStateException("onShutdown failed");
            }
        }
    }

    @Test
    void featuresStopBeforeTheShutdownHook() {
        TestBot bot = new TestBot(false, feature("a"), feature("b"));

        bot.onStart();
        bot.onEnd();

        assertEquals(List.of("start a", "start b", "stop a", "stop b", "onShutdown"), events);
    }

    @Test
    void aFeatureThatFailsToStartIsSkippedAndNotStopped() {
        TestBot bot = new TestBot(false, feature("a"), new RecordingFeature("broken", true, false), feature("c"));

        bot.onStart();
        bot.onEnd();

        assertEquals(List.of("start a", "start c", "stop a", "stop c", "onShutdown"), events);
    }

    @Test
    void aFeatureThatFailsToStopDoesNotStopTheRestOfShutdown() {
        TestBot bot = new TestBot(false, new RecordingFeature("broken", false, true), feature("b"));
        bot.onStart();

        assertDoesNotThrow(bot::onEnd);

        assertEquals(List.of("start broken", "start b", "stop b", "onShutdown"), events);
    }

    @Test
    void aFailingShutdownHookDoesNotEscape() {
        TestBot bot = new TestBot(true, feature("a"));
        bot.onStart();

        assertDoesNotThrow(bot::onEnd);
    }

    @Test
    void shutdownOnlyRunsOnce() {
        TestBot bot = new TestBot(false, feature("a"));
        bot.onStart();

        bot.onEnd();
        bot.onEnd();

        assertEquals(List.of("start a", "stop a", "onShutdown"), events);
    }

    @Test
    void shutdownBeforeStartAndWithoutADatabaseStillCompletes() {
        TestBot bot = new TestBot(false, feature("a"));

        assertDoesNotThrow(bot::onEnd);

        assertEquals(List.of("onShutdown"), events);
    }

    @Test
    void nullFeaturesAreIgnored() {
        TestBot bot = new TestBot(false, feature("a"));
        bot.features.add(null);

        assertDoesNotThrow(bot::onStart);
        bot.onEnd();

        assertEquals(List.of("start a", "stop a", "onShutdown"), events);
    }
}
