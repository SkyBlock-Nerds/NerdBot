package net.hypixel.nerdbot.app;

import jakarta.inject.Provider;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class FeatureSelectionTest {

    static class Configured extends BotFeature {
        @Override
        public void onFeatureStart() {
        }

        @Override
        public void onFeatureEnd() {
        }
    }

    static class AlwaysOn extends Configured {
    }

    @Test
    void configuredFeaturesComeFirstThenTheAlwaysOnOnes() {
        Configured configured = new Configured();
        AlwaysOn alwaysOn = new AlwaysOn();

        assertEquals(List.of(configured, alwaysOn), SkyBlockNerdsBot.combineFeatures(List.of(configured), List.of(alwaysOn)));
    }

    @Test
    void aConfigEntryForAnAlwaysOnFeatureDoesNotStartASecondCopy() {
        AlwaysOn alwaysOn = new AlwaysOn();

        assertEquals(List.of(alwaysOn), SkyBlockNerdsBot.combineFeatures(List.of(new AlwaysOn()), List.of(alwaysOn)));
    }

    @Test
    void configuredClassNamesAreLookedUpAmongTheInjectedFeatures() {
        Map<Class<?>, Provider<BotFeature>> available = Map.of(Configured.class, Configured::new);

        Optional<BotFeature> first = SkyBlockNerdsBot.findFeature(available, Configured.class.getName());
        Optional<BotFeature> second = SkyBlockNerdsBot.findFeature(available, Configured.class.getName());

        assertInstanceOf(Configured.class, first.orElseThrow());
        assertNotSame(first.get(), second.orElseThrow());
        assertEquals(Optional.empty(), SkyBlockNerdsBot.findFeature(available, "net.hypixel.nerdbot.app.feature.Unknown"));
    }
}
