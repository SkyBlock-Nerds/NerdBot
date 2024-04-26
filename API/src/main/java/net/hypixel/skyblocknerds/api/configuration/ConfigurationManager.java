package net.hypixel.skyblocknerds.discordbot.configuration;

import lombok.SneakyThrows;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigurationManager {

    private static final File CONFIG_PATH = new File(getCurrentDirectory(), "config");

    public static <T> T loadConfig(Class<T> configClass) throws IOException {
        String fileName = CONFIG_PATH.getAbsolutePath() + File.separator + convertClassName(configClass.getSimpleName()).toLowerCase() + ".json";
        File configFile = new File(fileName);

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();

            return createDefaultConfig(configClass, configFile);
        }

        try (FileReader reader = new FileReader(configFile)) {
            return SkyBlockNerdsAPI.GSON.fromJson(reader, configClass);
        } catch (Exception e) {
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
        String fileName = CONFIG_PATH + convertClassName(configClass.getClass().getSimpleName()).toLowerCase() + ".json";
        File configFile = new File(fileName);

        try (FileWriter writer = new FileWriter(configFile)) {
            SkyBlockNerdsAPI.GSON.toJson(configClass, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows
    public static @NotNull File getCurrentDirectory() {
        return new File(ConfigurationManager.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    }

    private static String convertClassName(String className) {
        return className.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }
}