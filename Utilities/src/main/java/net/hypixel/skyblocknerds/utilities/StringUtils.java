package net.hypixel.skyblocknerds.utilities;

import java.text.DecimalFormat;
import java.util.UUID;
import java.util.regex.Pattern;

public class StringUtils {

    public static final DecimalFormat COMMA_SEPARATED_DECIMAL_FORMAT = new DecimalFormat("#,###");
    public static final Pattern UUID_REGEX = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}");
    public static final Pattern TRIMMED_UUID_REGEX = Pattern.compile("[a-f0-9]{12}4[a-f0-9]{3}[89aAbB][a-f0-9]{15}");
    private static final Pattern ADD_UUID_HYPHENS_REGEX = Pattern.compile("([a-f0-9]{8})([a-f0-9]{4})(4[a-f0-9]{3})([89aAbB][a-f0-9]{3})([a-f0-9]{12})");

    /**
     * Checks if the input is a valid {@link UUID}.
     *
     * @param input input to check.
     * @return true if the input is a valid UUID.
     */
    public static boolean isUUID(String input) {
        return (input != null && !input.isEmpty()) && (input.matches(UUID_REGEX.pattern()) || input.matches(TRIMMED_UUID_REGEX.pattern()));
    }

    /**
     * Converts a string representation (with or without dashes) of a {@link UUID} to the {@link UUID} class.
     *
     * @param input unique id to convert.
     * @return converted unique id.
     */
    public static UUID toUUID(String input) {
        if (!isUUID(input)) {
            throw new IllegalArgumentException("Not a valid UUID!");
        }

        if (input.contains("-")) {
            return UUID.fromString(input); // Already has hyphens
        }

        return UUID.fromString(input.replaceAll(ADD_UUID_HYPHENS_REGEX.pattern(), "$1-$2-$3-$4-$5"));
    }

    /**
     * Formats a name with an ID.
     *
     * @param name The name.
     * @param id   The ID.
     * @return The formatted name with ID in parentheses.
     */
    public static String formatNameWithId(String name, String id) {
        return name + " (" + id + ")";
    }

    /**
     * Formats a number with commas.
     *
     * @param number The {@link Number} to format.
     * @return The formatted number with commas.
     */
    public static String formatNumberWithCommas(Number number) {
        return COMMA_SEPARATED_DECIMAL_FORMAT.format(number);
    }
}
