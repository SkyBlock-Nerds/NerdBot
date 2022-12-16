package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.MessageCache;

import javax.security.auth.login.LoginException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class NerdBotApp {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    public static final Gson GSON = new GsonBuilder().create();

    private static MessageCache messageCache;
    private static Bot bot;

    public static void main(String[] args) {
        NerdBot nerdBot = new NerdBot();
        bot = nerdBot;
        try {
            nerdBot.create(args);
            messageCache = new MessageCache();
        } catch (LoginException e) {
            log.error("Failed to find login for bot!");
            System.exit(-1);
        }

        Thread userSavingTask = new Thread(() -> {
            log.info("Attempting to save " + Database.USER_CACHE.estimatedSize() + " cached users");
            Database.USER_CACHE.asMap().forEach((s, discordUser) -> {
                Database.getInstance().updateUser(discordUser);
            });
        });
        Runtime.getRuntime().addShutdownHook(userSavingTask);
    }

    public static Bot getBot() {
        return bot;
    }

    public static MessageCache getMessageCache() {
        return messageCache;
    }
}
