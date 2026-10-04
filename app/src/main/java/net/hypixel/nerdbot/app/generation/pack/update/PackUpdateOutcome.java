package net.hypixel.nerdbot.app.generation.pack.update;

import org.jetbrains.annotations.Nullable;

/** Everything a pack update check or rollback can end in. */
public sealed interface PackUpdateOutcome {

    /**
     * Nothing to do: the selected version is live, or was rejected before. {@code sha1} is the
     * published SHA-1, or the deploy key for sources that publish none.
     */
    record UpToDate(String sha1, boolean previouslyRejected) implements PackUpdateOutcome {
    }

    /** A new pack is live. {@code from} is null when the configured pack was live before. */
    record Applied(@Nullable PackState.AppliedPack from, PackState.AppliedPack to, int itemCount, int previousItemCount) implements PackUpdateOutcome {
    }

    /**
     * The selected pack failed a check and is remembered so it is not retried. {@code sha1} is the
     * published SHA-1, or the deploy key for sources that publish none.
     */
    record Rejected(String sha1, int packFormat, String reason) implements PackUpdateOutcome {
    }

    /** A problem that may clear by itself (network, API anomaly, disk). Nothing is remembered. */
    record TransientFailure(String reason, @Nullable Throwable cause, int consecutiveFailures) implements PackUpdateOutcome {
    }

    /** An admin rollback succeeded. */
    record RolledBack(PackState.AppliedPack from, PackState.AppliedPack to) implements PackUpdateOutcome {
    }

    /** A rollback or check that cannot run, with the reason. */
    record Unavailable(String reason) implements PackUpdateOutcome {
    }

    /** Another check or rollback is already running. Only returned to the caller: never sent to the listener or recorded. */
    record Busy() implements PackUpdateOutcome {
    }
}
