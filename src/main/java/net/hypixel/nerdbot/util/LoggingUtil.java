package net.hypixel.nerdbot.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class LoggingUtil {

    public static void setLogLevelForConsole(Class<?> targetClass, Level level) {
        LoggerContext context = LoggerContext.getContext(false);
        Configuration config = context.getConfiguration();

        LoggerConfig loggerConfig = config.getLoggerConfig(targetClass.getName());
        loggerConfig.setLevel(level);
        context.updateLoggers();
    }
}
