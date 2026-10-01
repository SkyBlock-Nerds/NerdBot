package net.hypixel.nerdbot.app.generation.pack.update;

/** Receives every outcome the {@link PackUpdater} produces except {@code Busy}, from scheduled and manual runs alike. */
@FunctionalInterface
public interface PackUpdateListener {

    void onOutcome(String packId, PackUpdateOutcome outcome);
}
