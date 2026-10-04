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

    /** Identifies a version by deploy id and format, for sources that publish no SHA-1. */
    public static String deployKey(String deployId, int packFormat) {
        return deployId + "/" + packFormat;
    }

    /**
     * A pack zip that was live.
     *
     * @param fileName Name of the zip inside the cache directory
     * @param deployId Hypixel's deploy id, or "configured" for the pack seeded from the config path
     */
    public record AppliedPack(int packFormat, String sha1, String deployId, String fileName, long appliedAtEpochMs) {

        /** {@link PackState#deployKey} of this pack; works with a null deploy id from a hand-edited state file. */
        public String deployKey() {
            return PackState.deployKey(deployId, packFormat);
        }
    }

    /**
     * A pack version that will not be applied again.
     *
     * @param sha1                The zip's SHA-1, or the deploy key when it was refused before any download
     * @param settingsFingerprint The {@link RejectionFingerprint} of the settings the pack failed
     *                            under. The rejection only applies while the settings still have
     *                            this fingerprint, so fixing a config mistake lets the same pack be
     *                            tried again. Null (admin rollbacks) means it always applies.
     * @param deployKey           {@link PackState#deployKey} of the version, null for entries written
     *                            before sources without a published SHA-1 were supported
     */
    public record RejectedHash(String sha1, int packFormat, String reason, long rejectedAtEpochMs,
                               @Nullable String settingsFingerprint, @Nullable String deployKey) {

        public boolean appliesTo(String currentFingerprint) {
            return settingsFingerprint == null || settingsFingerprint.equals(currentFingerprint);
        }

        /**
         * Versions with a published SHA-1 match on it; versions without one match on deploy id and
         * format, since their SHA-1 is only known after downloading.
         */
        public boolean matches(@Nullable String candidateSha1, String candidateDeployKey) {
            return candidateSha1 != null ? sha1.equals(candidateSha1) : candidateDeployKey.equals(deployKey);
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

        /** The rejection of this version that still applies under the current settings, if any. */
        public Optional<RejectedHash> rejection(@Nullable String sha1, String deployKey, String currentFingerprint) {
            return rejected.stream()
                .filter(entry -> entry.matches(sha1, deployKey) && entry.appliesTo(currentFingerprint))
                .findFirst();
        }
    }
}
