package net.hypixel.skyblocknerds.api.configuration;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.utilities.Utilities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationManager {

    private static final File CONFIG_PATH = new File(Utilities.getCurrentDirectory(), "config");

    private static final Map<Class<?>, Object> configCache = new HashMap<>();

    public static <T> T loadConfig(Class<T> configClass) {
        if (configCache.containsKey(configClass)) {
            return configClass.cast(configCache.get(configClass));
        }

        String fileName = CONFIG_PATH + File.separator + convertClassNameToFileName(configClass.getSimpleName()) + ".json";
        File configFile = new File(fileName);

        if (!configFile.exists()) {
            return createDefaultConfig(configClass, configFile);
        }

        try (FileReader reader = new FileReader(configFile)) {
            T config = SkyBlockNerdsAPI.GSON.fromJson(reader, configClass);
            if (config == null) {
                // TODO swap to proper logger
                System.err.println("Failed to parse configuration " + configClass.getSimpleName() + " from file " + fileName + ", creating default configuration.");
                return createDefaultConfig(configClass, configFile);
            }
            configCache.put(configClass, config);
            return config;
        } catch (JsonIOException | JsonSyntaxException | IOException e) {
            e.printStackTrace();
            return createDefaultConfig(configClass, configFile);
        }
    }

    public static <T> void reloadConfig(Class<T> configClass) {
        configCache.remove(configClass);
        loadConfig(configClass);
    }

    private static <T> T createDefaultConfig(Class<T> configClass, File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            T defaultConfig = configClass.getDeclaredConstructor().newInstance();
            SkyBlockNerdsAPI.GSON.toJson(defaultConfig, writer);
            configCache.put(configClass, defaultConfig);
            return defaultConfig;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> void saveConfig(T config) {
        String fileName = CONFIG_PATH + File.separator + convertClassNameToFileName(config.getClass().getSimpleName()) + ".json";
        File configFile = new File(fileName);

        try (FileWriter writer = new FileWriter(configFile)) {
            SkyBlockNerdsAPI.GSON.toJson(config, writer);
            configCache.put(config.getClass(), config); // Update cache
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String convertClassNameToFileName(String className) {
        return className.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }
}