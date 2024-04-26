package net.hypixel.skyblocknerds.api.configuration;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.utilities.Utilities;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ConfigurationManager {

    private static final File CONFIG_PATH = new File(Utilities.getCurrentDirectory(), "config");

    public static <T> T loadConfig(Class<T> configClass) {
        String fileName = CONFIG_PATH.getAbsolutePath() + File.separator + convertClassToFileName(configClass.getSimpleName()).toLowerCase() + ".json";
        File configFile = new File(fileName);

        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return createDefaultConfig(configClass, configFile);
        }

        try (FileReader reader = new FileReader(configFile)) {
            T config = SkyBlockNerdsAPI.GSON.fromJson(reader, configClass);
            updateConfig(config, configClass);
            return config;
        } catch (JsonIOException | JsonSyntaxException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static <T> T createDefaultConfig(Class<T> configClass, File configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            T config = configClass.getConstructor().newInstance();
            SkyBlockNerdsAPI.GSON.toJson(config, writer);
            return config;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> void saveConfig(T configClass) {
        String fileName = CONFIG_PATH + File.separator + convertClassToFileName(configClass.getClass().getSimpleName()).toLowerCase() + ".json";
        File configFile = new File(fileName);

        try (FileWriter writer = new FileWriter(configFile)) {
            SkyBlockNerdsAPI.GSON.toJson(configClass, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String convertClassToFileName(String className) {
        return className.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }

    private static <T> void updateConfig(T config, Class<T> configClass) {
        try {
            T defaultConfig = configClass.getDeclaredConstructor().newInstance();
            Map<String, Object> defaultValues = getConfigValues(defaultConfig);
            Map<String, Object> currentValues = getConfigValues(config);

            for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
                if (!currentValues.containsKey(entry.getKey())) {
                    Field field = configClass.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    field.set(config, entry.getValue());
                }
            }

            saveConfig(config);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static <T> Map<String, Object> getConfigValues(T config) throws IllegalAccessException {
        Map<String, Object> values = new HashMap<>();
        Field[] fields = config.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            values.put(field.getName(), field.get(config));
        }
        return values;
    }
}