package net.hypixel.nerdbot.util;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.config.BotConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

public class Util {

    public static void sleep(TimeUnit unit, long time) {
        try {
            Thread.sleep(unit.toMillis(time));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static BotConfig loadConfig(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found!");
        }
        BufferedReader br = new BufferedReader(new FileReader(file.getPath()));
        return NerdBotApp.GSON.fromJson(br, BotConfig.class);
    }

}
