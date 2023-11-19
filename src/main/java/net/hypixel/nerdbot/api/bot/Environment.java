package net.hypixel.nerdbot.api.bot;

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
        if (System.getProperty("bot.environment") == null) {
            return DEV;
        }

        return Environment.valueOf(System.getProperty("bot.environment"));
    }
}
