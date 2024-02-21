package net.hypixel.nerdbot.generator.exception;

import java.util.Objects;

public class GeneratorException extends RuntimeException {

    public GeneratorException(String message) {
        super(message);
    }

    public GeneratorException(String message, String... formatArgs) {
        super(formatMessage(message, formatArgs));
    }

    public GeneratorException(String message, Throwable cause) {
        super(message, cause);
    }

    public static String formatMessage(String message, String... formatArgs) {
        for (int i = 0; i < formatArgs.length; i++) {
            String safeResponse = Objects.requireNonNullElse(formatArgs[i], "").replaceAll("`", "");
            formatArgs[i] = safeResponse.length() != 0 ? safeResponse : " ";
        }
        return String.format(message, (Object[]) formatArgs);
    }
}
