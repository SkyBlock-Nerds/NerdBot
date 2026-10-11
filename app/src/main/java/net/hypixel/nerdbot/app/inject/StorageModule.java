package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import net.hypixel.nerdbot.app.punishment.PunishmentStore;
import net.hypixel.nerdbot.app.punishment.RepositoryPunishmentStore;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;

@Module
public interface StorageModule {

    @Binds
    PunishmentStore punishmentStore(RepositoryPunishmentStore store);

    @Provides
    static PunishmentRepository punishmentRepository(Database database) {
        return repository(database, PunishmentRepository.class);
    }

    /**
     * Repositories are only registered when the database connected, so a missing one fails here
     * with a clear message instead of a null reaching the caller.
     */
    static <T> T repository(Database database, Class<T> type) {
        T repository = database.getRepositoryManager().getRepository(type);
        if (repository == null) {
            throw new IllegalStateException(type.getSimpleName() + " is not registered; is the database connected?");
        }
        return repository;
    }
}
