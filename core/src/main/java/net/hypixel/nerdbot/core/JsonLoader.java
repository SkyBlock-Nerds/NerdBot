package net.hypixel.nerdbot.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonLoader<T> {

    private static final Map<String, List<?>> cache = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().create();

    private JsonLoader() {

    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFromJson(Class<T[]> clazz, String filePath) throws IOException {
        if (cache.containsKey(filePath)) {
            return (List<T>) cache.get(filePath);
        }

        try (FileReader reader = new FileReader(filePath)) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = GSON.fromJson(reader, listType);
            cache.put(filePath, data);
            return data;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFromJson(Class<T[]> clazz, URL url) throws IOException {
        if (cache.containsKey(url.toString())) {
            return (List<T>) cache.get(url.toString());
        }

        try (InputStream is = url.openStream()) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);
            cache.put(url.toString(), data);
            return data;
        }
    }

    public static <T> void saveToJson(String filePath, List<T> list) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            GSON.toJson(list, writer);
        }
    }
}

