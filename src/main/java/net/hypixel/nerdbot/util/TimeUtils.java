package net.hypixel.nerdbot.util;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TimeUtils {

    public static final SimpleDateFormat GLOBAL_DATE_TIME_FORMAT = new SimpleDateFormat("d MMMM yyyy HH:mm a");

    private TimeUtils() {
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
            sb.append(days).append(days == 1 ? " day " : " days ");
        }

        if (hours > 0) {
            sb.append(hours).append(hours == 1 ? " hour " : " hours ");
        }

        if (minutes > 0) {
            sb.append(minutes).append(minutes == 1 ? " minute " : " minutes ");
        }

        sb.append(seconds).append(seconds == 1 ? " second" : " seconds");
        return sb.toString().trim();
    }

    public static boolean isAprilFirst() {
        return Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1;
    }

    public static boolean isDayOfMonth(int dayOfMonth) {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == dayOfMonth;
    }
}
