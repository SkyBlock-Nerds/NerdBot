package net.hypixel.nerdbot.app.inject;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import net.hypixel.nerdbot.app.punishment.PunishmentStore;
import net.hypixel.nerdbot.app.punishment.RepositoryPunishmentStore;
import net.hypixel.nerdbot.app.reminder.ReminderStore;
import net.hypixel.nerdbot.app.reminder.RepositoryReminderStore;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;
import net.hypixel.nerdbot.marmalade.storage.database.repository.ReminderRepository;

@Module
public interface StorageModule {

    @Binds
    PunishmentStore punishmentStore(RepositoryPunishmentStore store);

    @Binds
    ReminderStore reminderStore(RepositoryReminderStore store);

    @Provides
    static PunishmentRepository punishmentRepository(Database database) {
        return repository(database, PunishmentRepository.class);
    }

    @Provides
    static ReminderRepository reminderRepository(Database database) {
        return repository(database, ReminderRepository.class);
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
