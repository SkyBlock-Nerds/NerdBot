package net.hypixel.nerdbot.discord.storage.database.repository;

import com.mongodb.client.MongoClient;
import net.hypixel.nerdbot.discord.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.discord.storage.repository.Repository;

public class ReminderRepository extends Repository<Reminder> {

    public ReminderRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "reminders", "uuid");
    }

    @Override
    protected String getId(Reminder entity) {
        return entity.getUuid().toString();
    }
}