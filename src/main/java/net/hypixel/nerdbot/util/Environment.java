package net.hypixel.nerdbot.util;

public enum Environment {
    PRODUCTION,
    DEV;

    public static boolean isProduction() {
        return getEnvironment().equals(PRODUCTION);
    }

    public static boolean isDev() {
        return getEnvironment().equals(DEV);
    }

    public static Environment getEnvironment() {
        return Environment.valueOf(System.getProperty("bot.environment"));
    }
}
