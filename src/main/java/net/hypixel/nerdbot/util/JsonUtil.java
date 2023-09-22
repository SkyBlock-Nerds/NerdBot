package net.hypixel.nerdbot.util;

import com.google.gson.*;
import net.hypixel.nerdbot.NerdBotApp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class JsonUtil {

    private JsonUtil() {
    }

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

    public static List<Tuple<String, Object, Object>> findChangedValues(String oldContent, String newContent) {
        Map<String, Object> oldJson = parseJsonString(oldContent);
        Map<String, Object> newJson = parseJsonString(newContent);
        List<Tuple<String, Object, Object>> differences = new ArrayList<>();
        findDifferences(oldJson, newJson, "", differences);
        return differences;
    }

    public static Map<String, Object> parseJsonString(String json) {
        try {
            return convertObjectToMap(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonParseException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    public static Map<String, Object> convertObjectToMap(JsonObject jsonObject) {
        return jsonObject.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    JsonElement value = entry.getValue();

                    if (value.isJsonObject()) {
                        return convertObjectToMap(value.getAsJsonObject());
                    } else {
                        return value;
                    }
                }
            ));
    }

    public static void findDifferences(Map<String, Object> oldJson, Map<String, Object> newJson, String path, List<Tuple<String, Object, Object>> differences) {
        oldJson.forEach((key, oldValue) -> {
            if (!newJson.containsKey(key)) {
                differences.add(new Tuple<>(path + key, oldValue, null));
            } else if (oldValue instanceof Map && newJson.get(key) instanceof Map) {
                Map<String, Object> oldMap = (Map<String, Object>) oldValue;
                Map<String, Object> newMap = (Map<String, Object>) newJson.get(key);
                findDifferences(oldMap, newMap, path + key + ".", differences);
            } else if (!Objects.equals(oldValue, newJson.get(key))) {
                differences.add(new Tuple<>(path + key, oldValue, newJson.get(key)));
            }
        });

        newJson.forEach((key, newValue) -> {
            if (!oldJson.containsKey(key)) {
                differences.add(new Tuple<>(path + key, null, newValue));
            }
        });
    }
}
