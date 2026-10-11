package net.hypixel.nerdbot.tooling.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.aerh.imagegenerator.pack.PackId;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.generation.pack.PackConfigMapper;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.app.generation.pack.update.ReleaseSources;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated example-config.json has to be something the bot would actually accept. */
class GeneratedExampleConfigTest {

    private static final String JSON = DataSerialization.PRETTY_GSON.toJson(new ConfigGenerator().generate(NerdBotConfig.class));
    private static final NerdBotConfig CONFIG = DataSerialization.GSON.fromJson(JSON, NerdBotConfig.class);

    @Test
    void readsBackToTheSameJson() {
        assertEquals(JSON, DataSerialization.PRETTY_GSON.toJson(CONFIG));
    }

    @Test
    void noStringIsLeftBlank() {
        List<String> blanks = new ArrayList<>();
        collectBlankStrings(JsonParser.parseString(JSON), "$", blanks);

        assertEquals(List.of(), blanks);
    }

    @Test
    void examplePacksHaveValidIdsAndTheDefaultPackPointsAtOne() {
        GeneratorConfig.ResourcePackConfig packs = CONFIG.getGeneratorConfig().getResourcePacks();

        for (GeneratorConfig.PackDefinition pack : packs.getPacks()) {
            assertDoesNotThrow(() -> PackId.parse(PackConfigMapper.normalisedId(pack.getId())), pack.getId());
        }
        assertTrue(packs.getPacks().stream().anyMatch(pack -> pack.getId().equals(packs.getDefaultPack())), packs.getDefaultPack());
    }

    @Test
    void examplePackPassesTheAutoUpdateChecksOnceEnabled() {
        NerdBotConfig ownCopy = DataSerialization.GSON.fromJson(JSON, NerdBotConfig.class);
        GeneratorConfig.ResourcePackConfig packs = ownCopy.getGeneratorConfig().getResourcePacks();
        GeneratorConfig.PackDefinition pack = packs.getPacks().getFirst();
        pack.getAutoUpdate().setEnabled(true);

        assertNull(ReleaseSources.problem(pack.getAutoUpdate()));
        assertEquals(List.of(pack), PackUpdater.enabledDefinitions(packs, true));
    }

    @Test
    void autoUpdateLimitsAreUsable() {
        GeneratorConfig.AutoUpdateSettings settings = CONFIG.getGeneratorConfig().getResourcePacks().getAutoUpdate();

        assertTrue(settings.getMaxDownloadBytes() >= 1024 * 1024, "maxDownloadBytes " + settings.getMaxDownloadBytes());
        assertTrue(settings.getMaxRejectedHashes() > 0);
        assertTrue(settings.getApiUrl().startsWith("https://"));
    }

    @Test
    void aprilFoolsTimezoneIsARealZone() {
        assertDoesNotThrow(() -> ZoneId.of(CONFIG.getFunConfig().getAprilFoolsTimezone()));
    }

    @Test
    void exampleFeaturesAreRealFeaturesWithASanePeriod() throws ClassNotFoundException {
        for (FeatureConfig feature : CONFIG.getFeatures()) {
            assertTrue(BotFeature.class.isAssignableFrom(Class.forName(feature.getClassName())), feature.getClassName());
            assertTrue(feature.getPeriodMs() >= 60_000, "periodMs " + feature.getPeriodMs());
        }
    }

    @Test
    void activityTypeMapsToJda() {
        assertDoesNotThrow(() -> CONFIG.getActivityType().toJda());
    }

    private static void collectBlankStrings(JsonElement element, String path, List<String> blanks) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collectBlankStrings(entry.getValue(), path + "." + entry.getKey(), blanks);
            }
        } else if (element.isJsonArray()) {
            for (int i = 0; i < element.getAsJsonArray().size(); i++) {
                collectBlankStrings(element.getAsJsonArray().get(i), path + "[" + i + "]", blanks);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().isBlank()) {
            blanks.add(path);
        }
    }
}
