package net.hypixel.nerdbot.bot.config.generator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.bot.config.BotConfig;

import java.io.FileWriter;
import java.io.IOException;

@Log4j2
public class ConfigGenerator {

    public static void main(String[] args) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        BotConfig botConfig = new BotConfig();
        String json = gson.toJson(botConfig);

        if (isValidJson(json)) {
            log.info("The provided JSON string is valid!");

            try {
                writeJsonToFile(json);
            } catch (IOException exception) {
                log.error("Error writing JSON to file", exception);
            }
        } else {
            log.error("The provided JSON string is invalid: \n" + json + "\n");
            System.exit(-1);
        }
    }

    private static boolean isValidJson(String jsonStr) {
        try {
            JsonParser.parseString(jsonStr);
            return true;
        } catch (JsonSyntaxException exception) {
            log.error("Invalid JSON string: " + jsonStr);
            return false;
        }
    }

    private static void writeJsonToFile(String json) throws IOException {
        try (FileWriter writer = new FileWriter("./src/main/resources/example-config.json")) {
            writer.write(json);
            log.info("Created JSON file successfully!");
        }
    }
}
