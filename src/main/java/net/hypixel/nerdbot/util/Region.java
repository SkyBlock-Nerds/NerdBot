package net.hypixel.nerdbot.util;

public enum Region {
    PRODUCTION,
    DEV;

    public static boolean isProduction() {
        return getRegion().equals(PRODUCTION);
    }

    public static boolean isDev() {
        return getRegion().equals(DEV);
    }

    public static Region getRegion() {
        return Region.valueOf(System.getProperty("bot.region"));
    }
}
