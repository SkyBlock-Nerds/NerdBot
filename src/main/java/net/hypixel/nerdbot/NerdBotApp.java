package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.hypixel.nerdbot.bot.Bot;
import net.hypixel.nerdbot.bot.impl.NerdBot;
import net.hypixel.nerdbot.util.Logger;

import javax.security.auth.login.LoginException;

public class NerdBotApp {

    private static Bot bot;

    public static final Gson GSON = new GsonBuilder().create();

    public static void main(String[] args) {
        NerdBot nerdBot = new NerdBot();
        try {
            nerdBot.create(args);
        } catch (LoginException e) {
            Logger.error("Failed to find login for bot!");
            e.printStackTrace();
            System.exit(0);
            return;
        }
        nerdBot.registerListeners();

        bot = nerdBot;
    }

    public static Bot getBot() {
        return bot;
    }
}
