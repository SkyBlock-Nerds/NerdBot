package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.storage.GreenlitStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeGreenlitStore implements GreenlitStore {

    public final List<GreenlitMessage> saved = new CopyOnWriteArrayList<>();
    private CompletableFuture<List<GreenlitMessage>> loadResult = CompletableFuture.completedFuture(List.of());
    private boolean savesFail;

    public FakeGreenlitStore loads(CompletableFuture<List<GreenlitMessage>> result) {
        this.loadResult = result;
        return this;
    }

    public FakeGreenlitStore savesFail() {
        this.savesFail = true;
        return this;
    }

    @Override
    public CompletableFuture<List<GreenlitMessage>> loadAll() {
        return loadResult;
    }

    @Override
    public CompletableFuture<Void> save(GreenlitMessage message) {
        if (savesFail) {
            return CompletableFuture.failedFuture(new IllegalStateException("database unavailable"));
        }
        saved.add(message);
        return CompletableFuture.completedFuture(null);
    }
}
