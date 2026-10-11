package net.hypixel.nerdbot.app.generation.pack.update;

import net.aerh.imagegenerator.tools.pack.PackReloadResult;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.marmalade.exception.DownloadRejectedException;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.PACK_ID;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.packZip;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.sha1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sources that publish no SHA-1, where versions are identified by deploy id and pack format. */
class DeployIndexUpdateTest {

    private static final String DEPLOY_1 = "11111111-1111-1111-1111-111111111111";
    private static final String DEPLOY_2 = "22222222-2222-2222-2222-222222222222";
    private static final byte[] BUILD_1 = packZip(48, "build 1");
    private static final byte[] BUILD_2 = packZip(48, "build 2");

    @TempDir
    Path dir;

    private Path cacheDir;
    private GeneratorConfig.PackDefinition definition;
    private PackCacheStore store;
    private final FakePackDownloader downloader = new FakePackDownloader();
    private final FakeResourcePackService service = new FakeResourcePackService();
    private Result<PackRelease, HttpException> latest = Result.failure(new HttpException("nothing published"));
    private PackUpdater updater;

    @BeforeEach
    void setUp() {
        cacheDir = dir.resolve("cache");
        definition = PackFixtures.definition(PackFixtures.write(dir.resolve("configured/alpha.zip"), packZip(46, "configured")));
        store = PackCacheStore.open(cacheDir, 20);
        updater = new PackUpdater(service, () -> Result.success(List.of()), downloader, store, PackFixtures.settings(cacheDir),
            (packId, outcome) -> {
            }, Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC), (client, autoUpdate) -> () -> latest);
    }

    private static URI urlFor(String deployId) {
        return URI.create("https://resourcepacks.hypixel.net/SkyBlock/" + deployId + "/48.zip");
    }

    private void publish(String deployId, byte[] servedBytes) {
        downloader.serve(urlFor(deployId), servedBytes);
        latest = Result.success(new PackRelease(deployId, 0, List.of(new PackReleaseVersion(48, urlFor(deployId), null))));
    }

    @Test
    void appliesAReleaseAndRecordsTheSha1OfWhatWasDownloaded() throws IOException {
        publish(DEPLOY_1, BUILD_1);

        PackUpdateOutcome.Applied applied = assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertEquals(sha1(BUILD_1), applied.to().sha1());
        assertEquals(DEPLOY_1, applied.to().deployId());
        assertTrue(Files.isRegularFile(cacheDir.resolve(sha1(BUILD_1) + ".zip")));
        try (Stream<Path> files = Files.list(cacheDir)) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void theSameDeployIdAndFormatIsUpToDateWithoutDownloading() {
        publish(DEPLOY_1, BUILD_1);
        updater.checkForUpdate(definition);

        PackUpdateOutcome outcome = updater.checkForUpdate(definition);

        assertEquals(new PackUpdateOutcome.UpToDate(DEPLOY_1 + "/48", false), outcome);
        assertEquals(1, downloader.requested.size());
    }

    @Test
    void aNewDeployIdIsAppliedAndTheOldOneKeptForRollback() {
        publish(DEPLOY_1, BUILD_1);
        updater.checkForUpdate(definition);
        publish(DEPLOY_2, BUILD_2);

        PackUpdateOutcome.Applied applied = assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertEquals(DEPLOY_1, applied.from().deployId());
        assertEquals(DEPLOY_2, applied.to().deployId());
        assertEquals(sha1(BUILD_1), store.slot(PACK_ID).previous().sha1());
    }

    @Test
    void goingBackToAnEarlierDeployReusesItsCachedZip() {
        publish(DEPLOY_1, BUILD_1);
        updater.checkForUpdate(definition);
        publish(DEPLOY_2, BUILD_2);
        updater.checkForUpdate(definition);
        publish(DEPLOY_1, BUILD_1);

        PackUpdateOutcome.Applied applied = assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertEquals(DEPLOY_1, applied.to().deployId());
        assertEquals(2, downloader.requested.size());
    }

    @Test
    void aNewDeployWithIdenticalBytesCannotBeRolledBackToItself() {
        publish(DEPLOY_1, BUILD_1);
        updater.checkForUpdate(definition);
        publish(DEPLOY_2, BUILD_1);
        updater.checkForUpdate(definition);

        PackUpdateOutcome.Unavailable unavailable = assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));

        assertTrue(unavailable.reason().contains("same as the live one"), unavailable.reason());
    }

    @Test
    void aRefusedDownloadIsRejectedByDeployKeyAndNotRetried() {
        downloader.fail(urlFor(DEPLOY_1), new DownloadRejectedException("host not allowed", "host"));
        latest = Result.success(new PackRelease(DEPLOY_1, 0, List.of(new PackReleaseVersion(48, urlFor(DEPLOY_1), null))));

        PackUpdateOutcome.Rejected rejected = assertInstanceOf(PackUpdateOutcome.Rejected.class, updater.checkForUpdate(definition));
        PackUpdateOutcome second = updater.checkForUpdate(definition);

        assertEquals(DEPLOY_1 + "/48", rejected.sha1());
        assertEquals(new PackUpdateOutcome.UpToDate(DEPLOY_1 + "/48", true), second);
        assertEquals(1, downloader.requested.size());
    }

    @Test
    void aPackFailingValidationIsRejectedUnderItsDownloadedSha1() {
        service.answering(new PackReloadResult.Rejected("missing items"));
        publish(DEPLOY_1, BUILD_1);

        PackUpdateOutcome.Rejected rejected = assertInstanceOf(PackUpdateOutcome.Rejected.class, updater.checkForUpdate(definition));

        assertEquals(sha1(BUILD_1), rejected.sha1());
        assertEquals(DEPLOY_1 + "/48", store.slot(PACK_ID).rejected().getFirst().deployKey());
    }
}
