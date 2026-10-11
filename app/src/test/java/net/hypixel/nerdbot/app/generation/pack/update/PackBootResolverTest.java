package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.PACK_ID;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.packZip;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.sha1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackBootResolverTest {

    private static final byte[] CACHED = packZip(48, "cached");

    @TempDir
    Path dir;

    private PackCacheStore store;
    private GeneratorConfig.ResourcePackConfig config;

    @BeforeEach
    void setUp() {
        store = PackCacheStore.open(dir.resolve("cache"), 20);
        config = new GeneratorConfig.ResourcePackConfig();
        config.setPacks(List.of(PackFixtures.definition(dir.resolve("configured.zip"))));
    }

    private void recordCachedPack() {
        PackFixtures.write(dir.resolve("cache").resolve(sha1(CACHED) + ".zip"), CACHED);
        store.recordApplied(PACK_ID, new PackState.AppliedPack(48, sha1(CACHED), "d1", sha1(CACHED) + ".zip", 1L));
    }

    @Test
    void anIntactCachedPackIsLoadedInsteadOfTheConfiguredOne() {
        recordCachedPack();

        Map<String, Path> overrides = PackBootResolver.cachedPaths(config, store);

        assertEquals(Map.of(PACK_ID, dir.resolve("cache").resolve(sha1(CACHED) + ".zip")), overrides);
    }

    @Test
    void aMissingCachedPackFallsBackToTheConfiguredOneAndClearsCurrent() throws IOException {
        recordCachedPack();
        Files.delete(dir.resolve("cache").resolve(sha1(CACHED) + ".zip"));

        Map<String, Path> overrides = PackBootResolver.cachedPaths(config, store);

        assertTrue(overrides.isEmpty());
        assertNull(store.slot(PACK_ID).current());
    }

    @Test
    void packsThatWereNeverUpdatedLoadFromTheConfiguredPath() {
        assertTrue(PackBootResolver.cachedPaths(config, store).isEmpty());
    }

    @Test
    void aCachedPackThatFailedToRegisterFallsBackAndTheConfiguredPacksAreRegisteredAgain() {
        recordCachedPack();
        FakeResourcePackService service = new FakeResourcePackService();
        Map<String, Path> overrides = PackBootResolver.cachedPaths(config, store);

        PackBootResolver.repairUnregistered(config, overrides, service, store);

        assertNull(store.slot(PACK_ID).current());
        assertEquals(1, service.registrations.size());
    }

    @Test
    void nothingIsRepairedWhenThereWereNoOverrides() {
        FakeResourcePackService service = new FakeResourcePackService();

        PackBootResolver.repairUnregistered(config, Map.of(), service, store);

        assertTrue(service.registrations.isEmpty());
    }
}
