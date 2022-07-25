package net.hypixel.nerdbot.api.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.Channel;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.Logger;
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

    private final MongoCollection<GreenlitMessage> greenlitCollection;
    private final MongoCollection<ChannelGroup> channelCollection;
    private final MongoCollection<DiscordUser> userCollection;
    private final MongoClient mongoClient;

    private boolean connected;

    private Database() {
        ConnectionString connectionString = new ConnectionString(System.getProperty("mongodb.uri"));
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .codecRegistry(codecRegistry)
                .applyToServerSettings(builder -> {
                    builder.addServerMonitorListener(this);
                })
                .build();

        mongoClient = MongoClients.create(clientSettings);
        greenlitCollection = mongoClient.getDatabase("skyblockNerds").getCollection("greenlitMessages", GreenlitMessage.class);
        channelCollection = mongoClient.getDatabase("skyblockNerds").getCollection("channelGroups", ChannelGroup.class);
        userCollection = mongoClient.getDatabase("skyblockNerds").getCollection("users", DiscordUser.class);
    }


    public static Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    @Override
    public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent event) {
        connected = true;
        log("Heartbeat successful! Elapsed time: " + event.getElapsedTime(TimeUnit.MILLISECONDS) + "ms");
    }

    @Override
    public void serverHeartbeatFailed(ServerHeartbeatFailedEvent event) {
        error("Heartbeat failed! Reason: " + event.getThrowable().getMessage());

        NerdBotApp.getExecutorService().submit(() -> {
            if (connected) {
                TextChannel channel = ChannelManager.getChannel(Channel.CURATE);
                User user = Users.getUser(Users.AERH.getUserId());
                if (channel == null || user == null) {
                    error("Couldn't notify of database error on Discord!");
                    return;
                }
                channel.sendMessage(user.getAsMention() + " I lost connection to the database! Pls fix!").queue();
            }
            connected = false;
        });
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

    public List<Document> get(String collection, String field, Object value) {
        return mongoClient.getDatabase("skyblockNerds").getCollection(collection).find(Filters.eq(field, value)).into(new ArrayList<>());
    }

    public void insertGreenlitMessage(GreenlitMessage greenlitMessage) {
        greenlitCollection.insertOne(greenlitMessage);
        log("Inserted greenlit message " + greenlitMessage.getId());
    }

    public void insertGreenlitMessages(List<GreenlitMessage> greenlitMessages) {
        greenlitCollection.insertMany(greenlitMessages);
        log("Inserted " + greenlitMessages.size() + " greenlit messages");
    }

    public void deleteGreenlitMessage(String field, Object value) {
        greenlitCollection.deleteOne(Filters.eq(field, value));
        log("Deleted greenlit message " + field + ":" + value);
    }

    public List<GreenlitMessage> getGreenlitCollection() {
        return new ArrayList<>(this.greenlitCollection.find().into(new ArrayList<>()));
    }

    public ChannelGroup getChannelGroup(String channel) {
        return channelCollection.find(Filters.eq("name", channel)).first();
    }

    public void insertChannelGroup(ChannelGroup channelGroup) {
        channelCollection.insertOne(channelGroup);
        log("Inserted channel group " + channelGroup.getName());
    }

    public void insertChannelGroups(List<ChannelGroup> channelGroups) {
        channelCollection.insertMany(channelGroups);
        log("Inserted " + channelGroups.size() + " channel groups");
    }

    public void deleteChannelGroup(String field, Object value) {
        channelCollection.deleteOne(Filters.eq(field, value));
        log("Deleted channel group " + field + ":" + value);
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
        userCollection.replaceOne(Filters.eq(field, value), user);
        log("Updated user " + field + ":" + value);
    }

    public void updateUsers(List<DiscordUser> users) {
        for (DiscordUser user : users) {
            updateUser("discordId", user.getDiscordId(), user);
        }
        log("Updated " + users.size() + " users");
    }

    public void deleteUser(String field, Object value) {
        userCollection.deleteOne(Filters.eq(field, value));
        log("Deleted user " + field + ":" + value);
    }

    public void deleteUser(DiscordUser user) {
        deleteUser("discordId", user.getDiscordId());
    }

}
