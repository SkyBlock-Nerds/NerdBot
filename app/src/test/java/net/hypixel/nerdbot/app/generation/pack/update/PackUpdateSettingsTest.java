package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source config validation, which packs get auto-updated, and the rejection fingerprint. */
class PackUpdateSettingsTest {

    private static final String INDEX_URL = "https://example.com/index.json";
    private static final String TEMPLATE = "https://resourcepacks.hypixel.net/SkyBlock/{deployId}/{format}.zip";

    private static GeneratorConfig.PackDefinition pack(String id) {
        GeneratorConfig.PackDefinition definition = PackFixtures.definition(Path.of("/app/packs/" + id.replace(':', '_') + ".zip"));
        definition.setId(id);
        return definition;
    }

    private static GeneratorConfig.PackSourceSettings deployIndex(String url, String template) {
        GeneratorConfig.PackSourceSettings source = new GeneratorConfig.PackSourceSettings();
        source.setType("deploy-index");
        source.setUrl(url);
        source.setDownloadUrlTemplate(template);
        return source;
    }

    private static List<String> enabledIds(GeneratorConfig.PackDefinition... definitions) {
        GeneratorConfig.ResourcePackConfig config = new GeneratorConfig.ResourcePackConfig();
        config.setPacks(new ArrayList<>(Arrays.asList(definitions)));
        return PackUpdater.enabledDefinitions(config).stream().map(GeneratorConfig.PackDefinition::getId).toList();
    }

    @Test
    void noSourceBlockMeansTheHypixelApi() {
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:skyblock").getAutoUpdate();

        assertEquals("hypixel-api", ReleaseSources.typeOf(autoUpdate));
        assertNull(ReleaseSources.problem(autoUpdate));
        assertEquals("hypixel-api:SkyBlock", ReleaseSources.identity(autoUpdate));
    }

    @Test
    void sourceTypeIsTrimmedAndCaseInsensitive() {
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:alpha").getAutoUpdate();
        autoUpdate.setSource(deployIndex(INDEX_URL, TEMPLATE));
        autoUpdate.getSource().setType("  Deploy-Index ");

        assertEquals("deploy-index", ReleaseSources.typeOf(autoUpdate));
        assertNull(ReleaseSources.problem(autoUpdate));
    }

    @Test
    void sourceProblemsAreExplained() {
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:alpha").getAutoUpdate();

        autoUpdate.setHypixelPackId(" ");
        assertEquals("hypixelPackId is empty", ReleaseSources.problem(autoUpdate));

        autoUpdate.setSource(new GeneratorConfig.PackSourceSettings());
        assertTrue(ReleaseSources.problem(autoUpdate).startsWith("source.type is missing"));

        autoUpdate.getSource().setType("ftp");
        assertTrue(ReleaseSources.problem(autoUpdate).startsWith("unknown source type 'ftp'"));

        autoUpdate.setSource(deployIndex("", TEMPLATE));
        assertEquals("source.url is empty", ReleaseSources.problem(autoUpdate));

        autoUpdate.setSource(deployIndex("http://example.com/index.json", TEMPLATE));
        assertEquals("source.url must be an https URL", ReleaseSources.problem(autoUpdate));

        autoUpdate.setSource(deployIndex(INDEX_URL, "https://example.com/{deployId}.zip"));
        assertTrue(ReleaseSources.problem(autoUpdate).contains("must contain {deployId} and {format}"));

        autoUpdate.setSource(deployIndex(INDEX_URL, "http://example.com/{deployId}/{format}.zip"));
        assertEquals("source.downloadUrlTemplate must be an https URL", ReleaseSources.problem(autoUpdate));
    }

    @Test
    void anInvalidSourceCannotBeBuilt() {
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:alpha").getAutoUpdate();
        autoUpdate.setSource(new GeneratorConfig.PackSourceSettings());

        assertThrows(IllegalArgumentException.class, () -> ReleaseSources.forDefinition(() -> null, autoUpdate));
    }

    @Test
    void sourcesAreBuiltForTheirType() {
        GeneratorConfig.PackAutoUpdate hypixel = pack("hypixel:skyblock").getAutoUpdate();
        GeneratorConfig.PackAutoUpdate index = pack("hypixel:alpha").getAutoUpdate();
        index.setSource(deployIndex(INDEX_URL, TEMPLATE));

        assertInstanceOf(HypixelApiReleaseSource.class, ReleaseSources.forDefinition(() -> null, hypixel));
        assertInstanceOf(DeployIndexReleaseSource.class, ReleaseSources.forDefinition(() -> null, index));
    }

    @Test
    void onlyEnabledPacksWithAPathAreUpdated() {
        GeneratorConfig.PackDefinition disabled = pack("hypixel:disabled");
        disabled.getAutoUpdate().setEnabled(false);
        GeneratorConfig.PackDefinition noAutoUpdate = pack("hypixel:plain");
        noAutoUpdate.setAutoUpdate(null);
        GeneratorConfig.PackDefinition noPath = pack("hypixel:nopath");
        noPath.setPath(" ");
        noPath.getAutoUpdate().setHypixelPackId("NoPath");

        assertEquals(List.of("hypixel:skyblock"), enabledIds(pack("hypixel:skyblock"), disabled, noAutoUpdate, noPath, null));
    }

    @Test
    void packsWithInvalidSettingsAreSkipped() {
        GeneratorConfig.PackDefinition badId = pack("not a valid id!");
        badId.getAutoUpdate().setHypixelPackId("A");
        GeneratorConfig.PackDefinition ratioTooHigh = pack("hypixel:high");
        ratioTooHigh.getAutoUpdate().setMinItemRatio(1.5);
        ratioTooHigh.getAutoUpdate().setHypixelPackId("B");
        GeneratorConfig.PackDefinition ratioNaN = pack("hypixel:nan");
        ratioNaN.getAutoUpdate().setMinItemRatio(Double.NaN);
        ratioNaN.getAutoUpdate().setHypixelPackId("C");
        GeneratorConfig.PackDefinition badSource = pack("hypixel:badsource");
        badSource.getAutoUpdate().setSource(new GeneratorConfig.PackSourceSettings());

        assertEquals(List.of("hypixel:skyblock"), enabledIds(pack("hypixel:skyblock"), badId, ratioTooHigh, ratioNaN, badSource));
    }

    @Test
    void packsSharingAnIdOnceLowercasedAreBothSkipped() {
        GeneratorConfig.PackDefinition upper = pack("Hypixel:SkyBlock");
        upper.getAutoUpdate().setHypixelPackId("Other");

        assertEquals(List.of(), enabledIds(pack("hypixel:skyblock"), upper));
    }

    @Test
    void packsReadingTheSameSourceAreBothSkipped() {
        assertEquals(List.of(), enabledIds(pack("hypixel:skyblock"), pack("hypixel:copy")));
    }

    @Test
    void aNullConfigHasNoEnabledPacks() {
        assertEquals(List.of(), PackUpdater.enabledDefinitions(null));
        assertEquals(List.of(), PackUpdater.enabledDefinitions(new GeneratorConfig.ResourcePackConfig()));
    }

    @Test
    void fingerprintIgnoresOrderCaseDuplicatesAndBlanks() {
        GeneratorConfig.AutoUpdateSettings first = new GeneratorConfig.AutoUpdateSettings();
        first.setAllowedDownloadHosts(List.of("a.example", "B.example"));
        GeneratorConfig.AutoUpdateSettings second = new GeneratorConfig.AutoUpdateSettings();
        second.setAllowedDownloadHosts(new ArrayList<>(Arrays.asList(" b.example ", "A.EXAMPLE", "a.example", "", null)));
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:skyblock").getAutoUpdate();

        assertEquals(RejectionFingerprint.of(first, autoUpdate), RejectionFingerprint.of(second, autoUpdate));
    }

    @Test
    void fingerprintChangesWithEverySettingThatCanCauseARejection() {
        GeneratorConfig.AutoUpdateSettings settings = new GeneratorConfig.AutoUpdateSettings();
        GeneratorConfig.PackAutoUpdate autoUpdate = pack("hypixel:skyblock").getAutoUpdate();
        String original = RejectionFingerprint.of(settings, autoUpdate);

        autoUpdate.setMinItemRatio(0.75);
        String ratioChanged = RejectionFingerprint.of(settings, autoUpdate);
        autoUpdate.setSampleItems(List.of("minecraft:stone"));
        String samplesChanged = RejectionFingerprint.of(settings, autoUpdate);
        settings.setMaxDownloadBytes(1);
        String sizeChanged = RejectionFingerprint.of(settings, autoUpdate);
        settings.setAllowedDownloadHosts(List.of("other.example"));
        String hostsChanged = RejectionFingerprint.of(settings, autoUpdate);
        autoUpdate.setSource(deployIndex(INDEX_URL, TEMPLATE));
        String sourceChanged = RejectionFingerprint.of(settings, autoUpdate);

        List<String> all = List.of(original, ratioChanged, samplesChanged, sizeChanged, hostsChanged, sourceChanged);
        assertEquals(all.size(), all.stream().distinct().count(), all.toString());
        assertEquals(16, original.length());
    }

    @Test
    void sampleItemOrderDoesNotChangeTheFingerprint() {
        GeneratorConfig.AutoUpdateSettings settings = new GeneratorConfig.AutoUpdateSettings();
        GeneratorConfig.PackAutoUpdate first = pack("hypixel:skyblock").getAutoUpdate();
        first.setSampleItems(List.of("a", "b"));
        GeneratorConfig.PackAutoUpdate second = pack("hypixel:skyblock").getAutoUpdate();
        second.setSampleItems(List.of("b", "a"));

        assertEquals(RejectionFingerprint.of(settings, first), RejectionFingerprint.of(settings, second));
        second.setSampleItems(List.of("B", "a"));
        assertNotEquals(RejectionFingerprint.of(settings, first), RejectionFingerprint.of(settings, second));
    }
}
