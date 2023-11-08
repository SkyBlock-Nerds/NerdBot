package net.hypixel.nerdbot.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.api.database.model.reminder.Reminder;
import net.hypixel.nerdbot.api.repository.Repository;

public class ReminderRepository extends Repository<Reminder> {

    public ReminderRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "reminders", "uuid");
    }

    @Override
    protected String getId(Reminder entity) {
        return entity.getUuid().toString();
    }
}
