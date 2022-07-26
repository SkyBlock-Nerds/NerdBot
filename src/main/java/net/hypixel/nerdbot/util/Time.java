package net.hypixel.nerdbot.util;

import java.util.concurrent.TimeUnit;

public class Time {

    public static String formatNow() {
        long millis = System.currentTimeMillis();
        return String.format("[%02d:%02d:%02d]",
                TimeUnit.MILLISECONDS.toHours(millis) % 24,
                TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        );
    }

    public static String formatMs(long ms) {
        long days = TimeUnit.MILLISECONDS.toDays(ms);
        ms -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        ms -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
    }

}
