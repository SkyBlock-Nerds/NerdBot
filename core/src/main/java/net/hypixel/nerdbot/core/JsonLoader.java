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
import java.util.function.UnaryOperator;

public class JsonLoader<T> {

    private static final Map<String, List<?>> cache = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().create();

    private JsonLoader() {

    }

    public static <T> List<T> loadFromJson(Class<T[]> clazz, String filePath) throws IOException {
        return loadFromJson(clazz, filePath, builder -> builder);
    }

    public static <T> List<T> loadFromJson(Class<T[]> clazz, URL url) throws IOException {
        return loadFromJson(clazz, url, builder -> builder);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFromJson(Class<T[]> clazz, URL url, UnaryOperator<GsonBuilder> builderCustomizer) throws IOException {
        String cacheKey = url.toString() + builderCustomizer.hashCode();

        if (cache.containsKey(cacheKey)) {
            return (List<T>) cache.get(cacheKey);
        }

        Gson gson = builderCustomizer.apply(new GsonBuilder()).create();

        try (InputStream is = url.openStream()) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);
            cache.put(cacheKey, data);
            return data;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> loadFromJson(Class<T[]> clazz, String filePath, UnaryOperator<GsonBuilder> builderCustomizer) throws IOException {
        String cacheKey = filePath + builderCustomizer.hashCode();

        if (cache.containsKey(cacheKey)) {
            return (List<T>) cache.get(cacheKey);
        }

        Gson gson = builderCustomizer.apply(new GsonBuilder()).create();

        try (FileReader reader = new FileReader(filePath)) {
            Type listType = TypeToken.getParameterized(List.class, clazz.getComponentType()).getType();
            List<T> data = gson.fromJson(reader, listType);
            cache.put(cacheKey, data);
            return data;
        }
    }

    public static <T> void saveToJson(String filePath, List<T> list) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            GSON.toJson(list, writer);
        }
    }
}

