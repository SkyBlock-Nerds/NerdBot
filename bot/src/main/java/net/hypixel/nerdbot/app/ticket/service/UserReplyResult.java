package net.hypixel.nerdbot.app.ticket.service;

/**
 * Result of attempting to send a user reply.
 */
public record UserReplyResult(boolean isSuccess, String errorMessage) {

    public static UserReplyResult ok() {
        return new UserReplyResult(true, null);
    }

    public static UserReplyResult fail(String message) {
        return new UserReplyResult(false, message);
    }
}