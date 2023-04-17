package net.hypixel.nerdbot.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.NerdBotApp;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class JsonUtil {

    public static JsonObject readJsonFile(String filename) {
        try {
            Reader reader = new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8);
            JsonObject jsonObject = NerdBotApp.GSON.fromJson(reader, JsonObject.class);
            reader.close();
            return jsonObject;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeJsonFile(String filename, JsonObject jsonObject) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8)) {
            NerdBotApp.GSON.toJson(jsonObject, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static JsonObject setJsonValue(JsonObject jsonObject, String keyPath, JsonElement newValue) {
        String[] keys = keyPath.split("\\.");

        JsonElement currentElement = jsonObject;
        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            if (key.matches(".+\\[\\d+]")) {
                int arrayIndex = Integer.parseInt(key.replaceAll("[^\\d]", ""));
                key = key.replaceAll("\\[\\d+]", "");
                currentElement = currentElement.getAsJsonObject().getAsJsonArray(key).get(arrayIndex);
            } else {
                currentElement = currentElement.getAsJsonObject().get(key);
            }
        }

        String lastKey = keys[keys.length - 1];
        if (lastKey.matches(".+\\[\\d+]")) {
            int arrayIndex = Integer.parseInt(lastKey.replaceAll("[^\\d]", ""));
            lastKey = lastKey.replaceAll("\\[\\d+]", "");
            currentElement.getAsJsonObject().getAsJsonArray(lastKey).set(arrayIndex, newValue);
        } else {
            currentElement.getAsJsonObject().add(lastKey, newValue);
        }

        return jsonObject;
    }
}
