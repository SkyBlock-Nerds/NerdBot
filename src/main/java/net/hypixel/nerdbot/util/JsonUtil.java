package net.hypixel.nerdbot.util;

import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class JsonUtil {

    private JsonUtil() {
    }

    public static JsonObject readJsonFile(String filename) {
        try {
            Reader reader = new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8);
            JsonObject jsonObject = NerdBotApp.GSON.fromJson(reader, JsonObject.class);
            reader.close();
            return jsonObject;
        } catch (IOException exception) {
            log.error("Failed to read json file: " + filename, exception);
            return null;
        }
    }

    public static void writeJsonFile(String filename, JsonObject jsonObject) {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8)) {
            NerdBotApp.GSON.toJson(jsonObject, writer);
        } catch (IOException exception) {
            log.error("Failed to write json file: " + filename, exception);
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

    public static List<Tuple<String, Object, Object>> findChangedValues(Map<String, Object> oldJson, Map<String, Object> newJson, String path) {
        List<Tuple<String, Object, Object>> differences = new ArrayList<>();

        for (Map.Entry<String, Object> entry : oldJson.entrySet()) {
            String key = entry.getKey();
            Object oldValue = entry.getValue();

            if (!newJson.containsKey(key)) {
                differences.add(new Tuple<>(path + key, oldValue, null));
            } else {
                Object newValue = newJson.get(key);

                if (oldValue instanceof Map && newValue instanceof Map) {
                    @SuppressWarnings("unchecked") // Suppress unchecked warning
                    Map<String, Object> oldMap = (Map<String, Object>) oldValue;
                    Map<String, Object> newMap = (Map<String, Object>) newValue;
                    differences.addAll(findChangedValues(oldMap, newMap, path + key + "."));
                } else if (!Objects.equals(oldValue, newValue)) {
                    differences.add(new Tuple<>(path + key, oldValue, newValue));
                }
            }
        }

        for (Map.Entry<String, Object> entry : newJson.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();

            if (!oldJson.containsKey(key)) {
                differences.add(new Tuple<>(path + key, null, newValue));
            }
        }

        return differences;
    }


    public static Map<String, Object> parseStringToMap(String json) {
        try {
            return convertObjectToMap(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonParseException exception) {
            log.error("Failed to parse json string to map: " + json, exception);
            return Collections.emptyMap();
        }
    }

    public static JsonElement parseString(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            log.error("Failed to parse json string: " + json,  exception);
            return null;
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

    public static Object jsonToObject(File file, Class<?> clazz) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(file.getPath()));
        return NerdBotApp.GSON.fromJson(br, clazz);
    }

    public static JsonObject isJsonObject(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is actually a json object
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonObject()) {
            return null;
        }
        return foundItem.getAsJsonObject();
    }

    public static String isJsonString(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is a primitive type
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonPrimitive()) {
            return null;
        }
        return foundItem.getAsJsonPrimitive().getAsString();
    }

    public static JsonArray isJsonArray(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is an array
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonArray()) {
            return null;
        }
        return foundItem.getAsJsonArray();
    }

    public static JsonElement getIndexedElement(JsonElement element, String property, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr);
            JsonElement propertyElement = element.getAsJsonObject().get(property);
            if (propertyElement == null || !propertyElement.isJsonArray()) {
                return null;
            }
            return propertyElement.getAsJsonArray().get(index);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static JsonElement getNextElement(JsonElement element, String key) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject().get(key);
        } else if (element.isJsonArray()) {
            return getElementFromArray(element.getAsJsonArray(), key);
        }
        return null;
    }

    private static JsonElement getElementFromArray(JsonArray array, String indexStr) {
        try {
            int index = Integer.parseInt(indexStr);
            return array.get(index);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
