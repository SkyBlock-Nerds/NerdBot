package net.hypixel.nerdbot.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

public class LoggingUtil {

    public static void setGlobalLogLevel(Level level) {
        Configurator.setAllLevels(LogManager.getRootLogger().getName(), level);
    }

    public static void setLogLevelForClass(Class<?> clazz, Level level) {
        Configurator.setLevel(LogManager.getLogger(clazz).getName(), level);
    }
}
