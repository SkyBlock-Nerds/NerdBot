package net.hypixel.nerdbot.core.exception;

public class MojangProfileException extends Exception {

    public MojangProfileException(String message) {
        super(message);
    }

    public MojangProfileException(String message, Throwable cause) {
        super(message, cause);
    }

    public MojangProfileException(Throwable cause) {
        super(cause);
    }

    public MojangProfileException() {
        super();
    }
}