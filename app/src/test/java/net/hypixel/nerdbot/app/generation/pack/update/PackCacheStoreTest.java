package net.hypixel.nerdbot.app.generation.pack.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.packZip;
import static net.hypixel.nerdbot.app.generation.pack.update.PackFixtures.sha1;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackCacheStoreTest {

    private static final String PACK = "hypixel:skyblock";
    private static final byte[] A = packZip(46, "a");
    private static final byte[] B = packZip(48, "b");
    private static final byte[] C = packZip(48, "c");

    @TempDir
    Path dir;

    private PackCacheStore open() {
        return PackCacheStore.open(dir, 3);
    }

    private static PackState.AppliedPack applied(byte[] zip, String deployId) {
        return new PackState.AppliedPack(48, sha1(zip), deployId, sha1(zip) + ".zip", 1L);
    }

    private static PackState.RejectedHash rejected(String sha1) {
        return new PackState.RejectedHash(sha1, 48, "bad", 1L, "fp", "deploy/48");
    }

    private Path cached(byte[] zip) {
        return PackFixtures.write(dir.resolve(sha1(zip) + ".zip"), zip);
    }

    private void writeState(String json) throws IOException {
        Files.writeString(dir.resolve("pack-state.json"), json, StandardCharsets.UTF_8);
    }

    @Test
    void createsTheCacheDirectoryAndStartsEmpty() {
        Path nested = dir.resolve("a/b");

        PackCacheStore store = PackCacheStore.open(nested, 3);

        assertTrue(Files.isDirectory(nested));
        assertEquals(PackState.Slot.empty(), store.slot(PACK));
    }

    @Test
    void stateSurvivesAReopen() {
        PackCacheStore store = open();
        store.recordApplied(PACK, applied(A, "d1"));
        store.recordRejected(PACK, rejected(sha1(C)));

        PackState.Slot reloaded = open().slot(PACK);

        assertEquals(sha1(A), reloaded.current().sha1());
        assertEquals(sha1(C), reloaded.rejected().getFirst().sha1());
    }

    @Test
    void applyingMovesTheCurrentPackToPrevious() {
        PackCacheStore store = open();
        store.recordApplied(PACK, applied(A, "d1"));

        store.recordApplied(PACK, applied(B, "d2"));

        assertEquals(sha1(B), store.slot(PACK).current().sha1());
        assertEquals(sha1(A), store.slot(PACK).previous().sha1());
    }

    @Test
    void applyingAfterAFallbackKeepsTheExistingPrevious() {
        PackCacheStore store = open();
        store.recordPrevious(PACK, applied(A, "configured"));

        store.recordApplied(PACK, applied(B, "d2"));

        assertEquals(sha1(A), store.slot(PACK).previous().sha1());
    }

    @Test
    void rejectionsAreNewestFirstWithoutDuplicatesAndCapped() {
        PackCacheStore store = open();
        store.recordRejected(PACK, rejected("1"));
        store.recordRejected(PACK, rejected("2"));
        store.recordRejected(PACK, rejected("1"));
        store.recordRejected(PACK, rejected("3"));
        store.recordRejected(PACK, rejected("4"));

        List<String> shas = store.slot(PACK).rejected().stream().map(PackState.RejectedHash::sha1).toList();

        assertEquals(List.of("4", "3", "1"), shas);
    }

    @Test
    void aRejectionLimitBelowOneStillKeepsTheNewest() {
        PackCacheStore store = PackCacheStore.open(dir, 0);
        store.recordRejected(PACK, rejected("1"));
        store.recordRejected(PACK, rejected("2"));

        assertEquals(List.of("2"), store.slot(PACK).rejected().stream().map(PackState.RejectedHash::sha1).toList());
    }

    @Test
    void rollingBackSwapsPacksAndUnrejectsTheOneRolledBackTo() {
        PackCacheStore store = open();
        store.recordApplied(PACK, applied(A, "d1"));
        store.recordApplied(PACK, applied(B, "d2"));
        store.recordRejected(PACK, rejected(sha1(A)));
        store.recordRejected(PACK, new PackState.RejectedHash("d1/48", 48, "refused", 1L, "fp", "d1/48"));

        store.recordRolledBack(PACK, new PackState.RejectedHash(sha1(B), 48, "rolled back", 2L, null, "d2/48"));

        PackState.Slot slot = store.slot(PACK);
        assertEquals(sha1(A), slot.current().sha1());
        assertEquals(sha1(B), slot.previous().sha1());
        assertEquals(List.of(sha1(B)), slot.rejected().stream().map(PackState.RejectedHash::sha1).toList());
    }

    @Test
    void fallbackClearsOnlyTheCurrentPack() {
        PackCacheStore store = open();
        store.recordApplied(PACK, applied(A, "d1"));
        store.recordApplied(PACK, applied(B, "d2"));
        store.recordRejected(PACK, rejected("x"));

        store.recordFallback(PACK);

        PackState.Slot slot = store.slot(PACK);
        assertNull(slot.current());
        assertEquals(sha1(A), slot.previous().sha1());
        assertEquals(1, slot.rejected().size());
    }

    @Test
    void verifiedZipNeedsAMatchingSha1() {
        PackCacheStore store = open();
        cached(A);
        PackFixtures.write(dir.resolve(sha1(B) + ".zip"), C);

        assertTrue(store.verifiedZip(applied(A, "d1")).isPresent());
        assertTrue(store.verifiedZip(applied(B, "d2")).isEmpty());
        assertTrue(store.verifiedZip(applied(C, "d3")).isEmpty());
    }

    @Test
    void verifiedZipRefusesFileNamesOutsideTheCache() {
        PackCacheStore store = PackCacheStore.open(dir.resolve("cache"), 3);
        PackFixtures.write(dir.resolve("outside.zip"), A);

        PackState.AppliedPack escaping = new PackState.AppliedPack(48, sha1(A), "d1", "../outside.zip", 1L);

        assertTrue(store.verifiedZip(escaping).isEmpty());
    }

    @Test
    void verifiedZipTreatsAnInvalidFileNameAsMissing() {
        PackState.AppliedPack invalid = new PackState.AppliedPack(48, sha1(A), "d1", "bad\0name.zip", 1L);

        assertTrue(open().verifiedZip(invalid).isEmpty());
    }

    @Test
    void importCopyLeavesTheSourceAloneAndReusesAnExistingCopy() throws IOException {
        PackCacheStore store = open();
        Path source = PackFixtures.write(dir.resolve("configured/source.zip"), A);

        Path first = store.importCopy(source, sha1(A));
        Path second = store.importCopy(source, sha1(A));

        assertEquals(dir.resolve(sha1(A) + ".zip"), first);
        assertEquals(first, second);
        assertArrayEquals(A, Files.readAllBytes(source));
        assertArrayEquals(A, Files.readAllBytes(first));
    }

    @Test
    void deleteIfUnreferencedKeepsZipsThatArePointedAt() {
        PackCacheStore store = open();
        cached(A);
        cached(B);
        store.recordApplied(PACK, applied(A, "d1"));

        store.deleteIfUnreferenced(sha1(A));
        store.deleteIfUnreferenced(sha1(B));

        assertTrue(Files.exists(dir.resolve(sha1(A) + ".zip")));
        assertFalse(Files.exists(dir.resolve(sha1(B) + ".zip")));
    }

    @Test
    void cleanupRemovesUnreferencedZipsPartialsAndStateTempFiles() throws IOException {
        PackCacheStore store = open();
        cached(A);
        cached(B);
        cached(C);
        store.recordApplied(PACK, applied(A, "d1"));
        store.recordApplied(PACK, applied(B, "d2"));
        Files.writeString(dir.resolve("abc.zip.part"), "partial");
        Files.writeString(dir.resolve("pack-state.json.tmp"), "{}");
        Files.writeString(dir.resolve("notes.txt"), "keep me");

        store.cleanup();

        assertTrue(Files.exists(dir.resolve(sha1(A) + ".zip")));
        assertTrue(Files.exists(dir.resolve(sha1(B) + ".zip")));
        assertFalse(Files.exists(dir.resolve(sha1(C) + ".zip")));
        assertFalse(Files.exists(dir.resolve("abc.zip.part")));
        assertFalse(Files.exists(dir.resolve("pack-state.json.tmp")));
        assertTrue(Files.exists(dir.resolve("pack-state.json")));
        assertTrue(Files.exists(dir.resolve("notes.txt")));
    }

    @Test
    void anUnreadableStateFileIsTreatedAsEmptyAndBlocksCleanupUntilRewritten() throws IOException {
        cached(A);
        writeState("{ not json");
        PackCacheStore store = open();

        store.cleanup();

        assertEquals(PackState.Slot.empty(), store.slot(PACK));
        assertTrue(Files.exists(dir.resolve(sha1(A) + ".zip")));

        store.recordApplied(PACK, applied(B, "d2"));
        store.cleanup();

        assertFalse(Files.exists(dir.resolve(sha1(A) + ".zip")));
    }

    @Test
    void aStateFileWhoseRootIsNotAnObjectIsUnreadable() throws IOException {
        cached(A);
        writeState("[1, 2, 3]");
        PackCacheStore store = open();

        store.cleanup();

        assertTrue(Files.exists(dir.resolve(sha1(A) + ".zip")));
    }

    @Test
    void anEmptyStateFileIsJustEmpty() throws IOException {
        writeState("null");

        assertEquals(PackState.Slot.empty(), open().slot(PACK));
    }

    @Test
    void handEditedEntriesThatWouldBreakLaterCallsAreDropped() throws IOException {
        writeState("""
            {"packs": {
              "broken:slot": 5,
              "hypixel:skyblock": {
                "current": {"packFormat": 48, "deployId": "d1", "fileName": "x.zip"},
                "previous": {"packFormat": 46, "sha1": "%s", "deployId": "configured", "fileName": "%s.zip", "appliedAtEpochMs": 1},
                "rejected": [null, {"packFormat": 48}, {"sha1": "keep", "packFormat": 48, "reason": "bad", "rejectedAtEpochMs": 1}]
              }
            }}
            """.formatted(sha1(A), sha1(A)));

        PackCacheStore store = open();
        PackState.Slot slot = store.slot(PACK);

        assertNull(slot.current());
        assertEquals(sha1(A), slot.previous().sha1());
        assertEquals(List.of("keep"), slot.rejected().stream().map(PackState.RejectedHash::sha1).toList());
        assertEquals(PackState.Slot.empty(), store.slot("broken:slot"));
    }

    @Test
    void aSanitisedStateFileStillAllowsCleanup() throws IOException {
        cached(A);
        writeState("""
            {"packs": {"hypixel:skyblock": {"current": {"packFormat": 48}}}}
            """);
        PackCacheStore store = open();

        store.cleanup();

        assertFalse(Files.exists(dir.resolve(sha1(A) + ".zip")));
    }

    @Test
    void aFailedStateWriteLeavesNoTempFileAndKeepsTheInMemoryState() throws IOException {
        PackCacheStore store = open();
        Path stateFile = dir.resolve("pack-state.json");
        Files.createDirectories(stateFile);
        Files.writeString(stateFile.resolve("blocker"), "makes the atomic move fail");

        store.recordApplied(PACK, applied(A, "d1"));

        assertFalse(Files.exists(dir.resolve("pack-state.json.tmp")));
        assertEquals(sha1(A), store.slot(PACK).current().sha1());
    }

    @Test
    void sha1OfStreamsTheFile() throws IOException {
        assertEquals(sha1(B), PackCacheStore.sha1Of(cached(B)));
    }

    @Test
    void fileMatchesIsFalseForMissingFilesAndDirectories() throws IOException {
        assertFalse(PackCacheStore.fileMatches(dir.resolve("missing.zip"), sha1(A)));
        assertFalse(PackCacheStore.fileMatches(dir, sha1(A)));
    }
}
