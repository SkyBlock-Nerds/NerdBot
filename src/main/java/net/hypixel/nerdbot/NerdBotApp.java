package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.Logger;

import javax.security.auth.login.LoginException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NerdBotApp {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Bot bot;

    public static void main(String[] args) {
        NerdBot nerdBot = new NerdBot();
        bot = nerdBot;
        try {
            nerdBot.create(args);
        } catch (LoginException e) {
            Logger.error("Failed to find login for bot!");
            e.printStackTrace();
            System.exit(0);
        }
    }

    public static Bot getBot() {
        return bot;
    }

}
