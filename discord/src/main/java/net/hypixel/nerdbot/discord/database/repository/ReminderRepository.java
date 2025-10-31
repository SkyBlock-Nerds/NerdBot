package net.hypixel.nerdbot.discord.database.model.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.discord.database.model.reminder.Reminder;
import net.hypixel.nerdbot.core.api.repository.Repository;

public class ReminderRepository extends Repository<Reminder> {

    public ReminderRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "reminders", "uuid");
    }

    @Override
    protected String getId(Reminder entity) {
        return entity.getUuid().toString();
    }
}