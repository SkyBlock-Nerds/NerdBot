package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.api.database.model.reminder.Reminder;
import net.hypixel.nerdbot.api.repository.CachedMongoRepository;

public class ReminderRepository extends CachedMongoRepository<Reminder> {

    public ReminderRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "reminders");
    }

    @Override
    protected String getId(Reminder entity) {
        return entity.getUuid().toString();
    }
}
