package net.hypixel.nerdbot.generator.exception;

public class GeneratorTimeoutException extends GeneratorException {

    public GeneratorTimeoutException(String message) {
        super(message);
    }

    public GeneratorTimeoutException(String message, String... formatArgs) {
        super(message, formatArgs);
    }

    public GeneratorTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
