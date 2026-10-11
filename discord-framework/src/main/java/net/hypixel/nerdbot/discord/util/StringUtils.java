package net.hypixel.nerdbot.discord.util;

import lombok.experimental.UtilityClass;

import java.text.DecimalFormat;
import java.util.Locale;

@UtilityClass
public class StringUtils {

    /** @deprecated A shared DecimalFormat isn't thread-safe; use {@link #formatWithCommas(long)}. */
    @Deprecated(forRemoval = true)
    public static final DecimalFormat COMMA_SEPARATED_FORMAT = new DecimalFormat("#,###");

    public static String formatWithCommas(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }

    public static String toOneLine(String input) {
        if (input == null) {
            return null;
        }
        String s = input.replace('\n', ' ').replace('\r', ' ');
        return s.replaceAll("\\s{2,}", " ").trim();
    }
}