package net.hypixel.nerdbot.util;

import com.google.gson.reflect.TypeToken;
import net.hypixel.nerdbot.NerdBotApp;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonLoader<T> {

    private static final Map<String, List<?>> cache = new HashMap<>();

    private JsonLoader() {

    }

    public static <T> List<T> loadFromJson(Class<T[]> clazz, String filePath) throws IOException {
        if (cache.containsKey(filePath)) {
            return (List<T>) cache.get(filePath);
        }

        try (FileReader reader = new FileReader(filePath)) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = NerdBotApp.GSON.fromJson(reader, listType);
            cache.put(filePath, data);
            return data;
        }
    }

    public static <T> List<T> loadFromJson(Class<T[]> clazz, URL url) throws IOException {
        if (cache.containsKey(url.toString())) {
            return (List<T>) cache.get(url.toString());
        }

        try (FileReader reader = new FileReader(url.getFile())) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = NerdBotApp.GSON.fromJson(reader, listType);
            cache.put(url.toString(), data);
            return data;
        }
    }

    public static <T> void saveToJson(String filePath, List<T> list) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            NerdBotApp.GSON.toJson(list, writer);
        }
    }
}

