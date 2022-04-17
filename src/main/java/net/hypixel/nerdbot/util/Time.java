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

}
