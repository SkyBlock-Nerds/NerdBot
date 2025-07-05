package net.hypixel.nerdbot.util;

import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class TimeUtil {

    public static final SimpleDateFormat GLOBAL_DATE_TIME_FORMAT = new SimpleDateFormat("d MMMM yyyy HH:mm a");

    private TimeUtil() {
    }

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

    public static String formatMsCompact(long ms) {
        long days = TimeUnit.MILLISECONDS.toDays(ms);
        ms -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        ms -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static String formatMsLong(long ms) {
        long days = TimeUnit.MILLISECONDS.toDays(ms);
        ms -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        ms -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms);
        ms -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms);
        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("day").append(days == 1 ? " " : "s ");
        }

        if (hours > 0) {
            sb.append(hours).append("hour").append(hours == 1 ? " " : "s ");
        }
        
        if (minutes > 0) {
            sb.append(minutes).append("minute").append(minutes == 1 ? " " : "s ");
        }

        sb.append(seconds).append("second").append(seconds == 1 ? "" : "s");
        return sb.toString().trim();
    }
}
