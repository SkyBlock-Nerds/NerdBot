package net.hypixel.nerdbot.app.storage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.repository.GreenlitMessageRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class RepositoryGreenlitStore implements GreenlitStore {

    private final Provider<GreenlitMessageRepository> repository;

    @Inject
    public RepositoryGreenlitStore(Provider<GreenlitMessageRepository> repository) {
        this.repository = repository;
    }

    @Override
    public CompletableFuture<List<GreenlitMessage>> loadAll() {
        return withRepository(GreenlitMessageRepository::getAllDocumentsAsync);
    }

    @Override
    public CompletableFuture<Void> save(GreenlitMessage message) {
        return withRepository(greenlit -> greenlit.cacheAndSaveAsync(message).thenApply(result -> null));
    }

    private <T> CompletableFuture<T> withRepository(Function<GreenlitMessageRepository, CompletableFuture<T>> action) {
        GreenlitMessageRepository greenlit;
        try {
            greenlit = repository.get();
        } catch (IllegalStateException notRegistered) {
            return CompletableFuture.failedFuture(notRegistered);
        }
        return action.apply(greenlit);
    }
}
