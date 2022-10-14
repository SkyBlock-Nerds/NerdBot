package net.hypixel.nerdbot.api.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Environment;
import net.hypixel.nerdbot.util.Users;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Database implements ServerMonitorListener {

    private static Database instance;
    private boolean connected;

    private final MongoClient mongoClient;
    private final MongoCollection<GreenlitMessage> greenlitCollection;
    private final MongoCollection<ChannelGroup> channelCollection;
    private final MongoCollection<DiscordUser> userCollection;

    private Database() {
        ConnectionString connectionString = new ConnectionString(System.getProperty("mongodb.uri"));
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .applyToServerSettings(builder -> {
                    builder.heartbeatFrequency(Environment.isDev() ? 1 : 10, TimeUnit.SECONDS);
                    builder.addServerMonitorListener(this);
                })
                .build();

        mongoClient = MongoClients.create(clientSettings);
        MongoDatabase database = mongoClient.getDatabase("skyblockNerds_" + Environment.getRegion().name().toLowerCase());
        greenlitCollection = database.getCollection("greenlitMessages", GreenlitMessage.class);
        channelCollection = database.getCollection("channelGroups", ChannelGroup.class);
        userCollection = database.getCollection("users", DiscordUser.class);
    }


    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        if (!connected) {
            connected = true;
        }

        if (System.getProperty("mongodb.showHeartbeats") != null && Boolean.parseBoolean(System.getProperty("mongodb.showHeartbeats", "true"))) {
            log("Heartbeat successful! Elapsed time: " + event.getElapsedTime(TimeUnit.MILLISECONDS) + "ms");
        }
    }

    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        error("Heartbeat failed! Reason: " + event.getThrowable().getMessage());
        if (connected) {
            if (ChannelManager.getLogChannel() != null) {
                ChannelManager.getLogChannel().sendMessage(Users.getUser(Users.AERH.getUserId()).getAsMention() + " The database has disconnected!").queue();
            }
        }
        connected = false;
    }

    public void disconnect() {
        if (connected) {
            mongoClient.close();
            connected = false;
        }
    }

    private void log(String message) {
        Logger.info("[Database] " + message);
    }

    private void error(String message) {
        Logger.error("[Database] " + message);
    }

    public boolean isConnected() {
        return connected;
    }

    public void insertGreenlitMessage(GreenlitMessage greenlitMessage) {
        InsertOneResult result = greenlitCollection.insertOne(greenlitMessage);
        log("Inserted greenlit message " + result.getInsertedId());
    }

    public void insertGreenlitMessages(List<GreenlitMessage> greenlitMessages) {
        InsertManyResult result = greenlitCollection.insertMany(greenlitMessages);
        log("Inserted " + result.getInsertedIds().size() + " greenlit messages");
    }

    public void createOrUpdateGreenlitMessages(List<GreenlitMessage> messages) {
        for (GreenlitMessage message : messages) {
            if (greenlitCollection.find(Filters.eq("messageId", message.getMessageId())).first() == null) {
                insertGreenlitMessage(message);
            } else {
                updateGreenlitMessage(message);
            }
        }
    }

    public GreenlitMessage getGreenlitMessage(String id) {
        return greenlitCollection.find(Filters.eq("messageId", id)).first();
    }

    public void updateGreenlitMessage(GreenlitMessage greenlitMessage) {
        UpdateResult result = greenlitCollection.updateOne(Filters.eq("messageId", greenlitMessage.getMessageId()), new Document("$set", greenlitMessage));
        if (result.getMatchedCount() == 0) {
            log("Couldn't find greenlit message " + greenlitMessage.getId() + " to update");
        } else {
            log(result.getModifiedCount() + " greenlit message(s) updated");
        }
    }

    public void updateGreenlitMessages(List<GreenlitMessage> messages) {
        for (GreenlitMessage message : messages) {
            updateGreenlitMessage(message);
        }
    }

    public void deleteGreenlitMessage(String field, Object value) {
        DeleteResult result = greenlitCollection.deleteOne(Filters.eq(field, value));
        log(result.getDeletedCount() + " greenlit message(s) deleted");
    }

    public List<GreenlitMessage> getGreenlitCollection() {
        return new ArrayList<>(this.greenlitCollection.find().into(new ArrayList<>()));
    }

    public ChannelGroup getChannelGroup(String name) {
        return channelCollection.find(Filters.eq("name", name)).first();
    }

    public void insertChannelGroup(ChannelGroup channelGroup) {
        channelCollection.insertOne(channelGroup);
        log("Inserted channel group " + channelGroup.getName());
    }

    public void insertChannelGroups(List<ChannelGroup> channelGroups) {
        channelCollection.insertMany(channelGroups);
        log("Inserted " + channelGroups.size() + " channel groups");
    }

    public void deleteChannelGroup(ChannelGroup channelGroup) {
        deleteChannelGroup(channelGroup.getName());
    }

    public void deleteChannelGroup(String name) {
        channelCollection.deleteOne(Filters.eq("name", name));
        log("Deleted channel group " + name);
    }

    public List<ChannelGroup> getChannelGroups() {
        return this.channelCollection.find().into(new ArrayList<>());
    }

    public List<DiscordUser> getUsers() {
        return this.userCollection.find().into(new ArrayList<>());
    }

    public DiscordUser getUser(String field, Object value) {
        return this.userCollection.find(Filters.eq(field, value)).first();
    }

    public DiscordUser getUser(String id) {
        return getUser("discordId", id);
    }

    public void insertUser(DiscordUser user) {
        userCollection.insertOne(user);
        log("Inserted user " + user.getDiscordId());
    }

    public void updateUser(String field, Object value, DiscordUser user) {
        UpdateResult result = userCollection.replaceOne(Filters.eq(field, value), user);
        if (result.getMatchedCount() == 0) {
            log("Couldn't find user " + user.getDiscordId() + " to update");
        } else {
            log(result.getModifiedCount() + " user(s) updated");
        }
    }

    public void updateUsers(List<DiscordUser> users) {
        if (users.isEmpty()) return;

        for (DiscordUser user : users) {
            updateUser("discordId", user.getDiscordId(), user);
        }
    }

    public void deleteUser(String field, Object value) {
        userCollection.deleteOne(Filters.eq(field, value));
        log("Deleted user " + field + ":" + value);
    }

    public void deleteUser(DiscordUser user) {
        deleteUser("discordId", user.getDiscordId());
    }

}
