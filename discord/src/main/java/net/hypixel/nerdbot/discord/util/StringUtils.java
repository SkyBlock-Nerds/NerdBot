package net.hypixel.nerdbot.discord.util;

import net.dv8tion.jda.api.entities.Message;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StringUtils {

    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final DecimalFormat COMMA_SEPARATED_FORMAT = new DecimalFormat("#,###");

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    @Deprecated
    static final String MINECRAFT_USERNAME_REGEX = "^[a-zA-Z0-9_]{2,16}";
    @Deprecated
    static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    private StringUtils() {
    }

    public static List<String> splitString(String text, int size) {
        List<String> parts = new ArrayList<>();

        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(text.length(), i + size)));
        }

        return parts;
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getFirstLine(Message message) {
        String firstLine = message.getContentRaw().split("\\n")[0];

        if (firstLine.isEmpty()) {
            if (message.getEmbeds().get(0).getTitle() != null) {
                firstLine = message.getEmbeds().get(0).getTitle();
            } else {
                firstLine = "No Title Found";
            }
        }

        return (firstLine.length() > 30) ? firstLine.substring(0, 27) + "..." : firstLine;
    }

    public static String toOneLine(String input) {
        if (input == null) {
            return null;
        }
        String s = input.replace('\n', ' ').replace('\r', ' ');
        return s.replaceAll("\\s{2,}", " ").trim();
    }
}