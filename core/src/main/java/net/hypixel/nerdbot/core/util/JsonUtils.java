package net.hypixel.nerdbot.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.BotEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class JsonUtils {

    private static final ExecutorService jsonExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    private JsonUtils() {
    }

    public static JsonObject readJsonFile(String filename) {
        try (Reader reader = Files.newBufferedReader(Path.of(filename), StandardCharsets.UTF_8)) {
            return BotEnvironment.GSON.fromJson(reader, JsonObject.class);
        } catch (IOException exception) {
            log.error("Failed to read json file: {}", filename, exception);
            return null;
        }
    }

    public static CompletableFuture<JsonObject> readJsonFileAsync(String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try (Reader reader = Files.newBufferedReader(Path.of(filename), StandardCharsets.UTF_8)) {
                return BotEnvironment.GSON.fromJson(reader, JsonObject.class);
            } catch (IOException exception) {
                log.error("Failed to read json file: {}", filename, exception);
                throw new RuntimeException("Failed to read json file: " + filename, exception);
            }
        }, jsonExecutor);
    }

    public static void writeJsonFile(String filename, JsonObject jsonObject) {
        try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
            BotEnvironment.GSON.toJson(jsonObject, writer);
        } catch (IOException exception) {
            log.error("Failed to write json file: {}", filename, exception);
        }
    }

    public static CompletableFuture<Void> writeJsonFileAsync(String filename, JsonObject jsonObject) {
        return CompletableFuture.runAsync(() -> {
            try (Writer writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
                BotEnvironment.GSON.toJson(jsonObject, writer);
            } catch (IOException exception) {
                log.error("Failed to write json file: {}", filename, exception);
                throw new RuntimeException("Failed to write json file: " + filename, exception);
            }
        }, jsonExecutor);
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

                if (oldValue instanceof Map<?, ?> oldMap && newValue instanceof Map<?, ?> newMap) {
                    differences.addAll(findChangedValues((Map<String, Object>) oldMap, (Map<String, Object>) newMap, path + key + "."));
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
            log.error("Failed to parse json string: " + json, exception);
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
        return BotEnvironment.GSON.fromJson(br, clazz);
    }

    public static CompletableFuture<Object> jsonToObjectAsync(File file, Class<?> clazz) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                return BotEnvironment.GSON.fromJson(br, clazz);
            } catch (IOException e) {
                log.error("Error reading file: {}", file.getPath(), e);
                throw new RuntimeException("Error reading file: " + file.getPath(), e);
            }
        }, jsonExecutor);
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

    public static void shutdown() {
        jsonExecutor.shutdown();
        try {
            if (!jsonExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("JsonUtil executor did not terminate gracefully, forcing shutdown");
                jsonExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for JsonUtil executor termination");
            jsonExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}