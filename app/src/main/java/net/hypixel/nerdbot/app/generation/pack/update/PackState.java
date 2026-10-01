package net.hypixel.nerdbot.app.generation.pack.update;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The persisted contents of pack-state.json. Times are epoch millis because Gson cannot serialize java.time types. */
public record PackState(Map<String, Slot> packs) {

    public PackState {
        packs = packs == null ? Map.of() : Map.copyOf(packs);
    }

    /**
     * A pack zip that was live.
     *
     * @param fileName Name of the zip inside the cache directory
     * @param deployId Hypixel's deploy id, or "configured" for the pack seeded from the config path
     */
    public record AppliedPack(int packFormat, String sha1, String deployId, String fileName, long appliedAtEpochMs) {
    }

    /**
     * A pack hash that will not be applied again.
     *
     * @param settingsFingerprint The {@link RejectionFingerprint} of the settings the pack failed
     *                            under. The rejection only applies while the settings still have
     *                            this fingerprint, so fixing a config mistake lets the same pack be
     *                            tried again. Null (admin rollbacks) means it always applies.
     */
    public record RejectedHash(String sha1, int packFormat, String reason, long rejectedAtEpochMs,
                               @Nullable String settingsFingerprint) {

        public boolean appliesTo(String currentFingerprint) {
            return settingsFingerprint == null || settingsFingerprint.equals(currentFingerprint);
        }
    }

    /**
     * Per-pack state.
     *
     * @param current  The auto-updated pack that is live, null when the configured pack is live
     * @param previous The pack to roll back to, null when there is none
     * @param rejected Rejected hashes, newest first
     */
    public record Slot(@Nullable AppliedPack current, @Nullable AppliedPack previous, List<RejectedHash> rejected) {

        public Slot {
            rejected = rejected == null ? List.of() : List.copyOf(rejected);
        }

        public static Slot empty() {
            return new Slot(null, null, List.of());
        }

        /** The rejection of {@code sha1} that still applies under the current settings, if any. */
        public Optional<RejectedHash> rejection(String sha1, String currentFingerprint) {
            return rejected.stream()
                .filter(entry -> entry.sha1().equals(sha1) && entry.appliesTo(currentFingerprint))
                .findFirst();
        }
    }
}
