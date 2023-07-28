package net.hypixel.nerdbot.util.gson;

public class HttpException extends RuntimeException {

    public HttpException(String message) {
        super(message);
    }

    public HttpException(String message, Throwable cause) {
        super(message, cause);
    }

}
