package net.hypixel.nerdbot.app.testsupport;

import java.util.concurrent.CompletableFuture;

/** How a fake store answers writes. */
public enum WriteOutcome {
    ACKNOWLEDGED,
    UNACKNOWLEDGED,
    FAILED;

    /** Applies {@code change} and reports success, or reports this outcome without applying it. */
    public CompletableFuture<Boolean> apply(Runnable change) {
        return switch (this) {
            case ACKNOWLEDGED -> {
                change.run();
                yield CompletableFuture.completedFuture(true);
            }
            case UNACKNOWLEDGED -> CompletableFuture.completedFuture(false);
            case FAILED -> CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
        };
    }
}
