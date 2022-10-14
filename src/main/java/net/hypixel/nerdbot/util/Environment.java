package net.hypixel.nerdbot.util;

public enum Environment {
    PRODUCTION,
    DEV;

    public static boolean isProduction() {
        return getRegion().equals(PRODUCTION);
    }

    public static boolean isDev() {
        return getRegion().equals(DEV);
    }

    public static Environment getRegion() {
        return Environment.valueOf(System.getProperty("bot.region"));
    }
}
