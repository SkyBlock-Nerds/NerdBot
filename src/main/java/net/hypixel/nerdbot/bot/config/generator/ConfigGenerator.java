package net.hypixel.nerdbot.bot.config.generator;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;

import java.io.FileWriter;
import java.io.IOException;

public class ConfigGenerator {

    public static void main(String[] args) {
        BotConfig botConfig = new BotConfig();
        String json = NerdBotApp.GSON.toJson(botConfig);

        try (FileWriter writer = new FileWriter("./src/main/resources/example-config.json")) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Check if the generated config is valid
        BotConfig generatedConfig = NerdBotApp.GSON.fromJson(json, BotConfig.class);
        System.out.println(generatedConfig);

    }
}
