package net.hypixel.nerdbot.app.generation.pack.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the pack cache directory: zips named {@code <sha1>.zip}, in-flight downloads named
 * {@code <sha1>.zip.part}, and {@code pack-state.json}. Every method is synchronized; the state
 * file is rewritten atomically after every change.
 */
@Slf4j
public class PackCacheStore {

    private static final String STATE_FILE = "pack-state.json";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String PART_SUFFIX = ".zip.part";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path cacheDir;
    private final int maxRejectedHashes;
    private final Map<String, PackState.Slot> slots;
    // True while the state file exists but could not be used; cleanup is skipped so a transient
    // read failure cannot delete the zips the real state still points at
    private boolean stateUnreadable;

    private PackCacheStore(Path cacheDir, int maxRejectedHashes, Map<String, PackState.Slot> slots, boolean stateUnreadable) {
        this.cacheDir = cacheDir;
        this.maxRejectedHashes = Math.max(1, maxRejectedHashes);
        this.slots = new HashMap<>(slots);
        this.stateUnreadable = stateUnreadable;
    }

    /**
     * Creates the cache directory if needed and loads the state file. A missing state file means
     * empty state; an unreadable or malformed one is logged and treated as empty, and is
     * overwritten on the next change.
     *
     * @throws UncheckedIOException when the directory cannot be created
     */
    public static PackCacheStore open(Path cacheDir, int maxRejectedHashes) {
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the pack cache directory " + cacheDir, e);
        }
        LoadedState loaded = loadState(cacheDir.resolve(STATE_FILE));
        return new PackCacheStore(cacheDir, maxRejectedHashes, loaded.slots(), loaded.unreadable());
    }

    /** What {@link #loadState} found; {@code unreadable} is true when the file existed but could not be used. */
    private record LoadedState(Map<String, PackState.Slot> slots, boolean unreadable) {
    }

    private static LoadedState loadState(Path stateFile) {
        if (!Files.exists(stateFile)) {
            return new LoadedState(Map.of(), false);
        }

        try {
            JsonElement root = JsonParser.parseString(Files.readString(stateFile, StandardCharsets.UTF_8));
            if (root == null || root.isJsonNull()) {
                return new LoadedState(Map.of(), false);
            }
            if (!root.isJsonObject()) {
                throw new JsonParseException("The root of the state file is not a JSON object");
            }

            PackState state = GSON.fromJson(sanitise(root.getAsJsonObject()), PackState.class);
            return new LoadedState(state == null ? Map.of() : state.packs(), false);
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers JsonParseException, IllegalArgumentException and Gson's wrapped NPEs
            log.warn("Ignoring unreadable pack state file {}; the configured packs will be used until the next update", stateFile, e);
            return new LoadedState(Map.of(), true);
        }
    }

    /**
     * Drops the parts of a hand-edited state file that would make later calls fail, one warning per
     * dropped item: null slots, current/previous packs without a sha1 or file name, and rejected
     * entries that are null or have no sha1. This works on the parsed JSON because the records
     * reject null list and map entries while they are being built.
     */
    private static JsonObject sanitise(JsonObject root) {
        JsonElement packsElement = root.get("packs");
        if (packsElement == null || packsElement.isJsonNull()) {
            return root;
        }
        if (!packsElement.isJsonObject()) {
            throw new JsonParseException("The packs entry of the state file is not a JSON object");
        }

        JsonObject packs = packsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : new ArrayList<>(packs.entrySet())) {
            String packId = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                log.warn("Dropping pack state entry {} because it is not an object", packId);
                packs.remove(packId);
                continue;
            }

            JsonObject slot = entry.getValue().getAsJsonObject();
            for (String field : List.of("current", "previous")) {
                JsonElement pack = slot.get(field);
                if (pack != null && !pack.isJsonNull() && !hasStrings(pack, "sha1", "fileName")) {
                    log.warn("Dropping the {} pack of {} because it has no sha1 or file name", field, packId);
                    slot.remove(field);
                }
            }

            JsonElement rejected = slot.get("rejected");
            if (rejected != null && rejected.isJsonArray()) {
                JsonArray kept = new JsonArray();
                for (JsonElement item : rejected.getAsJsonArray()) {
                    if (hasStrings(item, "sha1")) {
                        kept.add(item);
                    } else {
                        log.warn("Dropping a rejected hash of {} because it is null or has no sha1", packId);
                    }
                }
                slot.add("rejected", kept);
            }
        }
        return root;
    }

    private static boolean hasStrings(JsonElement element, String... fields) {
        if (element == null || !element.isJsonObject()) {
            return false;
        }
        for (String field : fields) {
            JsonElement value = element.getAsJsonObject().get(field);
            if (value == null || !value.isJsonPrimitive()) {
                return false;
            }
        }
        return true;
    }

    public Path zipPath(String sha1) {
        return cacheDir.resolve(sha1 + ZIP_SUFFIX);
    }

    public Path partPath(String sha1) {
        return cacheDir.resolve(sha1 + PART_SUFFIX);
    }

    public synchronized PackState.Slot slot(String packId) {
        return slots.getOrDefault(packId, PackState.Slot.empty());
    }

    /** The current pack becomes previous and {@code applied} becomes current. */
    public synchronized void recordApplied(String packId, PackState.AppliedPack applied) {
        PackState.Slot slot = slot(packId);
        PackState.AppliedPack previous = slot.current() != null ? slot.current() : slot.previous();
        save(packId, new PackState.Slot(applied, previous, slot.rejected()));
    }

    /** Sets the rollback target without changing the current pack. */
    public synchronized void recordPrevious(String packId, PackState.AppliedPack previous) {
        PackState.Slot slot = slot(packId);
        save(packId, new PackState.Slot(slot.current(), previous, slot.rejected()));
    }

    public synchronized void recordRejected(String packId, PackState.RejectedHash rejected) {
        PackState.Slot slot = slot(packId);
        save(packId, new PackState.Slot(slot.current(), slot.previous(), withRejection(slot.rejected(), rejected)));
    }

    /**
     * Swaps current and previous, un-rejects the pack rolled back to and then rejects the one that
     * was current. The un-reject comes first so that a current pack sharing the previous pack's
     * deploy key (a new SHA-1 under the same deploy id) stays rejected.
     */
    public synchronized void recordRolledBack(String packId, PackState.RejectedHash rejectedCurrent) {
        PackState.Slot slot = slot(packId);
        List<PackState.RejectedHash> kept = new ArrayList<>(slot.rejected());
        if (slot.previous() != null) {
            // The admin just endorsed the pack being rolled back to, so it must not stay rejected
            String previousDeployKey = PackState.deployKey(slot.previous().deployId(), slot.previous().packFormat());
            kept.removeIf(entry -> entry.sha1().equals(slot.previous().sha1()) || previousDeployKey.equals(entry.deployKey()));
        }
        save(packId, new PackState.Slot(slot.previous(), slot.current(), withRejection(kept, rejectedCurrent)));
    }

    /**
     * Clears the current pack, keeping previous and the rejected hashes. Called when the cached
     * current pack could not be loaded at boot and the configured pack is live instead, so the next
     * check re-applies the update rather than reporting it up to date.
     */
    public synchronized void recordFallback(String packId) {
        PackState.Slot slot = slot(packId);
        if (slot.current() == null) {
            return;
        }
        save(packId, new PackState.Slot(null, slot.previous(), slot.rejected()));
    }

    private List<PackState.RejectedHash> withRejection(List<PackState.RejectedHash> existing, PackState.RejectedHash added) {
        List<PackState.RejectedHash> updated = new ArrayList<>();
        updated.add(added);
        existing.stream()
            .filter(entry -> !entry.sha1().equals(added.sha1()))
            .limit(maxRejectedHashes - 1L)
            .forEach(updated::add);
        return updated;
    }

    /** The cached zip for {@code applied}, only if it exists and its SHA-1 still matches. */
    public synchronized Optional<Path> verifiedZip(PackState.AppliedPack applied) {
        try {
            Path zip = cacheDir.resolve(applied.fileName());
            if (!zip.normalize().startsWith(cacheDir.normalize())) {
                return Optional.empty();
            }
            return fileMatches(zip, applied.sha1()) ? Optional.of(zip) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers InvalidPathException from a hand-edited file name
            log.warn("Could not verify cached pack {}", applied.fileName(), e);
            return Optional.empty();
        }
    }

    /** {@code <sha1>.zip}, only if it exists and its SHA-1 matches. */
    public synchronized Optional<Path> cachedZip(String sha1) {
        try {
            Path zip = zipPath(sha1);
            return fileMatches(zip, sha1) ? Optional.of(zip) : Optional.empty();
        } catch (IOException e) {
            log.warn("Could not verify cached pack {}{}", sha1, ZIP_SUFFIX, e);
            return Optional.empty();
        }
    }

    /** Whether {@code file} is a regular file whose SHA-1 is {@code sha1}. */
    public static boolean fileMatches(Path file, String sha1) throws IOException {
        return Files.isRegularFile(file) && sha1Of(file).equals(sha1);
    }

    /** Atomically renames {@code <sha1>.zip.part} to {@code <sha1>.zip}, replacing it, and returns the zip. */
    public synchronized Path promotePart(String sha1) throws IOException {
        return promotePart(sha1, sha1);
    }

    /**
     * Atomically renames {@code <partKey>.zip.part} to {@code <sha1>.zip}, replacing it, and returns
     * the zip. The part key differs from the SHA-1 for downloads whose SHA-1 is only known afterwards.
     */
    public synchronized Path promotePart(String partKey, String sha1) throws IOException {
        Path zip = zipPath(sha1);
        Files.move(partPath(partKey), zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return zip;
    }

    /** Deletes {@code <sha1>.zip.part} if it exists, logging instead of failing. */
    public synchronized void deletePart(String sha1) {
        deleteQuietly(partPath(sha1));
    }

    /**
     * Copies {@code source} into the cache as {@code <sha1>.zip} through a partial file, unless a
     * zip with that SHA-1 is already cached. The source itself is never modified.
     *
     * @return the cached zip
     */
    public synchronized Path importCopy(Path source, String sha1) throws IOException {
        Optional<Path> existing = cachedZip(sha1);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            Files.copy(source, partPath(sha1), StandardCopyOption.REPLACE_EXISTING);
            return promotePart(sha1);
        } catch (IOException e) {
            deletePart(sha1);
            throw e;
        }
    }

    /** Deletes {@code <sha1>.zip} unless some pack's current or previous slot points at it. */
    public synchronized void deleteIfUnreferenced(String sha1) {
        if (referencedFileNames().contains(sha1 + ZIP_SUFFIX)) {
            return;
        }
        deleteQuietly(zipPath(sha1));
    }

    /**
     * Deletes every zip that no current or previous slot references, and every leftover partial
     * download. A file still open (Windows, during a release grace period) is left for next time.
     */
    public synchronized void cleanup() {
        if (stateUnreadable) {
            log.warn("Skipping pack cache cleanup in {} until the unreadable state file has been rewritten", cacheDir);
            return;
        }

        Set<String> keep = referencedFileNames();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(cacheDir)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                boolean staleZip = name.endsWith(ZIP_SUFFIX) && !keep.contains(name);
                if (staleZip || name.endsWith(PART_SUFFIX)) {
                    deleteQuietly(file);
                }
            }
        } catch (IOException e) {
            log.warn("Could not list the pack cache directory {} for cleanup", cacheDir, e);
        }
    }

    private Set<String> referencedFileNames() {
        Set<String> names = new HashSet<>();
        for (PackState.Slot slot : slots.values()) {
            if (slot.current() != null) {
                names.add(slot.current().fileName());
            }
            if (slot.previous() != null) {
                names.add(slot.previous().fileName());
            }
        }
        return names;
    }

    private void save(String packId, PackState.Slot slot) {
        slots.put(packId, slot);
        Path stateFile = cacheDir.resolve(STATE_FILE);
        Path temp = cacheDir.resolve(STATE_FILE + ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(new PackState(slots)), StandardCharsets.UTF_8);
            Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            stateUnreadable = false;
        } catch (IOException e) {
            // The in-memory state stays correct for this run; only a restart would lose it
            log.error("Could not write the pack state file {}", stateFile, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Could not delete {} yet, will retry on the next cleanup: {}", file, e.getMessage());
        }
    }

    /** Lowercase hex SHA-1 of a file, streamed. */
    public static String sha1Of(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available in this JVM", e);
        }
    }
}
