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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.HYPIXEL_PACK_ID;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.PACK_ID;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.packZip;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.sha1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackUpdaterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
    private static final byte[] CONFIGURED = packZip(46, "configured");
    private static final byte[] V2 = packZip(48, "v2");
    private static final byte[] V3 = packZip(48, "v3");

    @TempDir
    Path dir;

    private Path cacheDir;
    private GeneratorConfig.PackDefinition definition;
    private PackCacheStore store;
    private final FakePackDownloader downloader = new FakePackDownloader();
    private final FakeResourcePackService service = new FakeResourcePackService();
    private final List<PackUpdateOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
    private Result<List<HypixelPack>, HttpException> packList = Result.failure(new HttpException("nothing published"));
    private PackUpdateListener listener = (packId, outcome) -> outcomes.add(outcome);
    private PackUpdater updater;

    @BeforeEach
    void setUp() {
        cacheDir = dir.resolve("cache");
        definition = PackFixtures.definition(PackFixtures.write(dir.resolve("configured/skyblock.zip"), CONFIGURED));
        store = PackCacheStore.open(cacheDir, 20);
        updater = newUpdater();
    }

    private PackUpdater newUpdater() {
        return new PackUpdater(service, () -> packList, downloader, store, PackFixtures.settings(cacheDir),
            (packId, outcome) -> listener.onOutcome(packId, outcome), CLOCK);
    }

    private static URI urlFor(byte[] bytes) {
        return URI.create("https://resourcepacks.hypixel.net/SkyBlock/" + sha1(bytes) + ".zip");
    }

    private HypixelPackVersion served(int packFormat, byte[] bytes) {
        downloader.serve(urlFor(bytes), bytes);
        return new HypixelPackVersion(packFormat, sha1(bytes), urlFor(bytes));
    }

    private void publish(String deployId, HypixelPackVersion... versions) {
        packList = Result.success(List.of(new HypixelPack(HYPIXEL_PACK_ID, deployId, 0, List.of(versions))));
    }

    private boolean anyPartFiles() throws IOException {
        try (Stream<Path> files = Files.list(cacheDir)) {
            return files.anyMatch(file -> file.getFileName().toString().endsWith(".part"));
        }
    }

    @Test
    void appliesANewerPackAndKeepsTheConfiguredOneForRollback() throws IOException {
        publish("deploy-2", served(48, V2));

        PackUpdateOutcome.Applied applied = assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertNull(applied.from());
        assertEquals(sha1(V2), applied.to().sha1());
        assertEquals("deploy-2", applied.to().deployId());
        assertEquals(48, applied.to().packFormat());
        assertEquals(100, applied.itemCount());
        assertEquals(90, applied.previousItemCount());

        PackState.Slot slot = store.slot(PACK_ID);
        assertEquals(sha1(V2), slot.current().sha1());
        assertEquals(sha1(CONFIGURED), slot.previous().sha1());
        assertEquals("configured", slot.previous().deployId());
        assertEquals(46, slot.previous().packFormat());
        assertTrue(Files.isRegularFile(cacheDir.resolve(sha1(V2) + ".zip")));
        assertTrue(Files.isRegularFile(cacheDir.resolve(sha1(CONFIGURED) + ".zip")));
        assertFalse(anyPartFiles());
    }

    @Test
    void reloadUsesThePacksValidationSettings() {
        publish("deploy-2", served(48, V2));

        updater.checkForUpdate(definition);

        FakeResourcePackService.Reload reload = service.reloads.getFirst();
        assertEquals(48, reload.expectations().expectedPackFormat());
        assertEquals(0.5, reload.expectations().minItemRatio());
        assertEquals(List.of("minecraft:diamond_sword"), reload.expectations().sampleItems());
        assertEquals(cacheDir.resolve(sha1(V2) + ".zip"), reload.zip());
    }

    @Test
    void everyOutcomeIsSentToTheListenerAndRemembered() {
        publish("deploy-2", served(48, V2));

        PackUpdateOutcome outcome = updater.checkForUpdate(definition);

        assertEquals(List.of(outcome), outcomes);
        assertEquals(outcome, updater.lastOutcome(PACK_ID).orElseThrow().outcome());
        assertEquals(CLOCK.millis(), updater.lastOutcome(PACK_ID).orElseThrow().atEpochMs());
    }

    @Test
    void reportsUpToDateWithoutDownloadingWhenTheLiveVersionIsStillLatest() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);

        PackUpdateOutcome outcome = updater.checkForUpdate(definition);

        assertEquals(new PackUpdateOutcome.UpToDate(sha1(V2), false), outcome);
        assertEquals(1, downloader.requested.size());
        assertEquals(1, service.reloads.size());
    }

    @Test
    void adoptsTheConfiguredPackWhenItAlreadyIsTheLatestVersion() {
        publish("deploy-1", served(46, CONFIGURED));

        PackUpdateOutcome outcome = updater.checkForUpdate(definition);

        assertEquals(new PackUpdateOutcome.UpToDate(sha1(CONFIGURED), false), outcome);
        assertTrue(downloader.requested.isEmpty());
        assertTrue(service.reloads.isEmpty());
        PackState.AppliedPack current = store.slot(PACK_ID).current();
        assertEquals(sha1(CONFIGURED), current.sha1());
        assertEquals("deploy-1", current.deployId());
        assertTrue(Files.isRegularFile(cacheDir.resolve(sha1(CONFIGURED) + ".zip")));
    }

    @Test
    void picksTheHighestPackFormat() {
        byte[] older = packZip(46, "older format");
        publish("deploy-2", served(46, older), served(48, V2));

        PackUpdateOutcome.Applied applied = assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertEquals(48, applied.to().packFormat());
        assertEquals(List.of(urlFor(V2)), downloader.requested);
    }

    @Test
    void refusesToGuessBetweenTwoVersionsWithTheTopFormat() {
        publish("deploy-2", served(48, V2), served(48, V3));

        PackUpdateOutcome.TransientFailure failure = assertInstanceOf(PackUpdateOutcome.TransientFailure.class, updater.checkForUpdate(definition));

        assertTrue(failure.reason().contains("refusing to guess"), failure.reason());
        assertTrue(downloader.requested.isEmpty());
    }

    @Test
    void reusesAZipThatIsAlreadyCached() {
        PackFixtures.write(cacheDir.resolve(sha1(V2) + ".zip"), V2);
        publish("deploy-2", served(48, V2));

        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertTrue(downloader.requested.isEmpty());
    }

    @Test
    void aCorruptCachedZipIsDownloadedAgain() {
        PackFixtures.write(cacheDir.resolve(sha1(V2) + ".zip"), new byte[]{0, 1, 2});
        publish("deploy-2", served(48, V2));

        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertEquals(List.of(urlFor(V2)), downloader.requested);
    }

    @Test
    void rejectsADownloadWhoseSha1DoesNotMatchThePublishedOne() throws IOException {
        URI url = urlFor(V2);
        downloader.serve(url, V3);
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));

        PackUpdateOutcome.Rejected rejected = assertInstanceOf(PackUpdateOutcome.Rejected.class, updater.checkForUpdate(definition));

        assertEquals(sha1(V2), rejected.sha1());
        assertTrue(rejected.reason().contains(sha1(V3)), rejected.reason());
        assertTrue(service.reloads.isEmpty());
        assertFalse(anyPartFiles());
        assertNull(store.slot(PACK_ID).current());
    }

    @Test
    void aRejectedVersionIsNotDownloadedAgain() {
        URI url = urlFor(V2);
        downloader.serve(url, V3);
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));
        updater.checkForUpdate(definition);

        PackUpdateOutcome outcome = updater.checkForUpdate(definition);

        assertEquals(new PackUpdateOutcome.UpToDate(sha1(V2), true), outcome);
        assertEquals(1, downloader.requested.size());
    }

    @Test
    void aRejectionStopsApplyingOnceTheUpdateSettingsChange() {
        URI url = urlFor(V2);
        downloader.serve(url, V3);
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));
        updater.checkForUpdate(definition);

        definition.getAutoUpdate().setMinItemRatio(0.4);
        downloader.serve(url, V2);

        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));
    }

    @Test
    void aDownloadRefusedByTheHostOrSizeRulesIsRejected() throws IOException {
        URI url = urlFor(V2);
        downloader.fail(url, new DownloadRejectedException("too large", "size"));
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));

        PackUpdateOutcome.Rejected rejected = assertInstanceOf(PackUpdateOutcome.Rejected.class, updater.checkForUpdate(definition));

        assertEquals(sha1(V2), rejected.sha1());
        assertTrue(service.reloads.isEmpty());
        assertFalse(anyPartFiles());
    }

    @Test
    void aNetworkFailureIsTransientAndLeavesNoPartialFile() throws IOException {
        URI url = urlFor(V2);
        downloader.fail(url, new HttpException("connection reset"));
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));

        PackUpdateOutcome.TransientFailure failure = assertInstanceOf(PackUpdateOutcome.TransientFailure.class, updater.checkForUpdate(definition));

        assertEquals(1, failure.consecutiveFailures());
        assertTrue(store.slot(PACK_ID).rejected().isEmpty());
        assertFalse(anyPartFiles());
    }

    @Test
    void consecutiveTransientFailuresCountUpAndResetAfterASuccess() {
        URI url = urlFor(V2);
        downloader.fail(url, new HttpException("connection reset"));
        publish("deploy-2", new HypixelPackVersion(48, sha1(V2), url));

        assertEquals(1, ((PackUpdateOutcome.TransientFailure) updater.checkForUpdate(definition)).consecutiveFailures());
        assertEquals(2, ((PackUpdateOutcome.TransientFailure) updater.checkForUpdate(definition)).consecutiveFailures());

        downloader.serve(url, V2);
        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        packList = Result.failure(new HttpException("API down"));
        assertEquals(1, ((PackUpdateOutcome.TransientFailure) updater.checkForUpdate(definition)).consecutiveFailures());
    }

    @Test
    void aPackListFailureIsTransient() {
        packList = Result.failure(new HttpException("API down"));

        PackUpdateOutcome.TransientFailure failure = assertInstanceOf(PackUpdateOutcome.TransientFailure.class, updater.checkForUpdate(definition));

        assertTrue(failure.reason().contains("API down"), failure.reason());
    }

    @Test
    void aPackFailingValidationIsRejectedAndItsZipRemoved() {
        service.answering(new PackReloadResult.Rejected("only 3 items"));
        publish("deploy-2", served(48, V2));

        PackUpdateOutcome.Rejected rejected = assertInstanceOf(PackUpdateOutcome.Rejected.class, updater.checkForUpdate(definition));

        assertEquals("only 3 items", rejected.reason());
        assertNull(store.slot(PACK_ID).current());
        assertEquals(sha1(V2), store.slot(PACK_ID).rejected().getFirst().sha1());
        assertFalse(Files.exists(cacheDir.resolve(sha1(V2) + ".zip")));
        assertTrue(Files.exists(cacheDir.resolve(sha1(CONFIGURED) + ".zip")));
    }

    @Test
    void aReloadThatWasNotAttemptedIsTransientAndNotRemembered() {
        service.answering(new PackReloadResult.NotAttempted("pack is not registered"));
        publish("deploy-2", served(48, V2));

        assertInstanceOf(PackUpdateOutcome.TransientFailure.class, updater.checkForUpdate(definition));

        assertTrue(store.slot(PACK_ID).rejected().isEmpty());
        assertFalse(Files.exists(cacheDir.resolve(sha1(V2) + ".zip")));
    }

    @Test
    void aReloadThatThrowsIsTransient() {
        service.throwing(new IllegalStateException("boom"));
        publish("deploy-2", served(48, V2));

        PackUpdateOutcome.TransientFailure failure = assertInstanceOf(PackUpdateOutcome.TransientFailure.class, updater.checkForUpdate(definition));

        assertTrue(failure.reason().contains("boom"), failure.reason());
        assertNull(store.slot(PACK_ID).current());
    }

    @Test
    void aSecondRunWhileOneIsInProgressIsBusyAndNotReported() throws InterruptedException {
        CountDownLatch reloading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        service.answering(zip -> {
            reloading.countDown();
            await(release);
            return FakeResourcePackService.applied(100, 90);
        });
        publish("deploy-2", served(48, V2));

        Thread first = new Thread(() -> updater.checkForUpdate(definition));
        first.start();
        assertTrue(reloading.await(5, TimeUnit.SECONDS));

        PackUpdateOutcome second = updater.checkForUpdate(definition);
        PackUpdateOutcome rollback = updater.rollback(definition);
        release.countDown();
        first.join(5_000);

        assertInstanceOf(PackUpdateOutcome.Busy.class, second);
        assertInstanceOf(PackUpdateOutcome.Busy.class, rollback);
        assertEquals(1, outcomes.size());
        assertInstanceOf(PackUpdateOutcome.Applied.class, outcomes.getFirst());
    }

    @Test
    void aFailingListenerDoesNotBreakTheCheck() {
        listener = (packId, outcome) -> {
            throw new IllegalStateException("log channel missing");
        };
        publish("deploy-2", served(48, V2));

        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));
        assertTrue(updater.lastOutcome(PACK_ID).isPresent());
    }

    @Test
    void rollbackSwapsBackAndRejectsThePackItRolledAwayFrom() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);

        PackUpdateOutcome.RolledBack rolledBack = assertInstanceOf(PackUpdateOutcome.RolledBack.class, updater.rollback(definition));

        assertEquals(sha1(V2), rolledBack.from().sha1());
        assertEquals(sha1(CONFIGURED), rolledBack.to().sha1());
        PackState.Slot slot = store.slot(PACK_ID);
        assertEquals(sha1(CONFIGURED), slot.current().sha1());
        assertEquals(sha1(V2), slot.previous().sha1());
        PackState.RejectedHash rejected = slot.rejected().getFirst();
        assertEquals(sha1(V2), rejected.sha1());
        assertNull(rejected.settingsFingerprint());
    }

    @Test
    void rollbackValidatesThePreviousPackWithoutTheItemRatio() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);

        updater.rollback(definition);

        FakeResourcePackService.Reload reload = service.reloads.getLast();
        assertEquals(46, reload.expectations().expectedPackFormat());
        assertEquals(0, reload.expectations().minItemRatio());
    }

    @Test
    void aRolledBackVersionStaysRejectedEvenAfterTheSettingsChange() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        updater.rollback(definition);

        definition.getAutoUpdate().setMinItemRatio(0.1);

        assertEquals(new PackUpdateOutcome.UpToDate(sha1(V2), true), updater.checkForUpdate(definition));
    }

    @Test
    void rollingBackAgainRestoresAndUnrejectsTheNewerPack() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        updater.rollback(definition);

        PackUpdateOutcome.RolledBack rolledBack = assertInstanceOf(PackUpdateOutcome.RolledBack.class, updater.rollback(definition));

        assertEquals(sha1(V2), rolledBack.to().sha1());
        assertTrue(store.slot(PACK_ID).rejected().stream().noneMatch(entry -> entry.sha1().equals(sha1(V2))));
    }

    @Test
    void rollbackIsUnavailableBeforeAnyUpdate() {
        PackUpdateOutcome.Unavailable unavailable = assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));

        assertTrue(unavailable.reason().contains("no automatic update"), unavailable.reason());
    }

    @Test
    void rollbackIsUnavailableWhenThePreviousFileIsGone() throws IOException {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        Files.delete(cacheDir.resolve(sha1(CONFIGURED) + ".zip"));

        PackUpdateOutcome.Unavailable unavailable = assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));

        assertTrue(unavailable.reason().contains("missing"), unavailable.reason());
        assertEquals(sha1(V2), store.slot(PACK_ID).current().sha1());
    }

    @Test
    void rollbackIsUnavailableWhenThePreviousPackFailsValidation() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        service.answering(new PackReloadResult.Rejected("broken"));

        assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));

        assertEquals(sha1(V2), store.slot(PACK_ID).current().sha1());
        assertTrue(store.slot(PACK_ID).rejected().isEmpty());
    }

    @Test
    void rollbackIsUnavailableWhenReloadingThrows() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        service.throwing(new IllegalStateException("boom"));

        assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));

        assertEquals(sha1(V2), store.slot(PACK_ID).current().sha1());
    }

    @Test
    void aRollbackDoesNotResetTheTransientFailureCount() {
        publish("deploy-2", served(48, V2));
        updater.checkForUpdate(definition);
        packList = Result.failure(new HttpException("API down"));
        updater.checkForUpdate(definition);

        updater.rollback(definition);

        assertEquals(2, ((PackUpdateOutcome.TransientFailure) updater.checkForUpdate(definition)).consecutiveFailures());
    }

    @Test
    void aConfiguredDirectoryCannotBeSeededSoTheFirstUpdateHasNoRollback() {
        definition.setPath(dir.toString());
        publish("deploy-2", served(48, V2));

        assertInstanceOf(PackUpdateOutcome.Applied.class, updater.checkForUpdate(definition));

        assertNull(store.slot(PACK_ID).previous());
        assertInstanceOf(PackUpdateOutcome.Unavailable.class, updater.rollback(definition));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
