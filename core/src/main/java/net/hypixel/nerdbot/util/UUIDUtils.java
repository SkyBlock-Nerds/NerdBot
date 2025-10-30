package net.hypixel.nerdbot.util;

import java.util.UUID;
import java.util.regex.Pattern;

public class UUIDUtils {

    public static final Pattern UUID_REGEX = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}");
    public static final Pattern TRIMMED_UUID_REGEX = Pattern.compile("[a-f0-9]{12}4[a-f0-9]{3}[89aAbB][a-f0-9]{15}");
    private static final Pattern ADD_UUID_HYPHENS_REGEX = Pattern.compile("([a-f0-9]{8})([a-f0-9]{4})(4[a-f0-9]{3})([89aAbB][a-f0-9]{3})([a-f0-9]{12})");

    private UUIDUtils() {
    }

    public static boolean isUUID(String input) {
        return (input != null && !input.isEmpty()) && (input.matches(UUID_REGEX.pattern()) || input.matches(TRIMMED_UUID_REGEX.pattern()));
    }

    public static UUID toUUID(String input) {
        if (!isUUID(input)) {
            throw new IllegalArgumentException("Not a valid UUID!");
        }

        if (input.contains("-")) {
            return UUID.fromString(input); // Already has hyphens
        }

        return UUID.fromString(input.replaceAll(ADD_UUID_HYPHENS_REGEX.pattern(), "$1-$2-$3-$4-$5"));
    }
}