package net.hypixel.nerdbot.bot.config.generator;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.hypixel.nerdbot.bot.config.BotConfig;

import java.io.FileWriter;
import java.io.IOException;

public class ConfigGenerator {

    public static void main(String[] args) {
        Gson gson = new Gson();
        BotConfig botConfig = new BotConfig();
        String json = gson.toJson(botConfig);

        if (isValidJson(json)) {
            System.out.println("The provided JSON string is valid!");

            try {
                writeJsonToFile(json);
            } catch (IOException e) {
                System.err.println("Error writing JSON to file:");
                e.printStackTrace();
            }
        } else {
            System.err.println("The provided JSON string is invalid!");
            System.exit(-1);
        }
    }

    private static boolean isValidJson(String jsonStr) {
        try {
            JsonParser.parseString(jsonStr);
            return true;
        } catch (JsonSyntaxException exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private static void writeJsonToFile(String json) throws IOException {
        try (FileWriter writer = new FileWriter("./src/main/resources/example-config.json")) {
            writer.write(json);
            System.out.println("Created JSON file successfully!");
        }
    }
}
