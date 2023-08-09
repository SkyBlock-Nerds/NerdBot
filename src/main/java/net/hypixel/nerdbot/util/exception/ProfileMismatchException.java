package net.hypixel.nerdbot.util.exception;

public class ProfileMismatchException extends Exception {

    public ProfileMismatchException(String message) {
        super(message);
    }

    public ProfileMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
