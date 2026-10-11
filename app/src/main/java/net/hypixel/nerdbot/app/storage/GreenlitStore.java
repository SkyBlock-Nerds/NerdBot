package net.hypixel.nerdbot.app.storage;

import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GreenlitStore {

    /** Every greenlit suggestion; fails, rather than throwing, when the database isn't available. */
    CompletableFuture<List<GreenlitMessage>> loadAll();

    /** Caches and writes the message straight away; fails, rather than throwing, when the database isn't available. */
    CompletableFuture<Void> save(GreenlitMessage message);
}
