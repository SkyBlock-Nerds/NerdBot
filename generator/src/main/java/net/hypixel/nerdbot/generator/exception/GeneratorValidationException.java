package net.hypixel.nerdbot.generator.exception;

public class GeneratorValidationException extends GeneratorException {

    public GeneratorValidationException(String message, String... formatArgs) {
        super(message, formatArgs);
    }
}