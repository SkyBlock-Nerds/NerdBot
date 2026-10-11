package net.hypixel.nerdbot.app.command.util;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * How commands answer, so every command handles failure the same way:
 * <ul>
 *     <li>Outcomes the user should hear about, such as bad input, come back from the service as a
 *     result and are shown as a normal reply.</li>
 *     <li>An unexpected exception thrown by a handler is left to the command library, which logs it
 *     and shows a generic error, even after the reply was deferred.</li>
 *     <li>Work that finishes later goes through {@link #finish}, because the library never sees
 *     exceptions from a future and the user would be left on "thinking..." forever.</li>
 *     <li>An exception a command catches itself goes through {@link #fail}, so it is logged and the
 *     user sees a safe message rather than the exception's text.</li>
 * </ul>
 */
@Slf4j
public final class CommandReplies {

    private CommandReplies() {
    }

    /** Acknowledges the interaction privately and returns the hook for the real answer. */
    public static InteractionHook deferPrivately(IReplyCallback interaction) {
        interaction.deferReply(true).queue();
        return interaction.getHook();
    }

    public static void replyPrivately(IReplyCallback interaction, String message) {
        interaction.reply(message).setEphemeral(true).queue();
    }

    /**
     * Logs the error (the logback Sentry appender forwards it to Sentry) and replaces the deferred
     * reply with {@code userMessage}. The message must be safe to show: never the exception's own
     * text, a stack trace or a file path.
     */
    public static void fail(InteractionHook hook, String userMessage, Throwable error) {
        logFailure(userMessage, error);
        hook.editOriginal(userMessage).queue();
    }

    /** As {@link #fail(InteractionHook, String, Throwable)}, for an interaction that wasn't deferred. */
    public static void fail(IReplyCallback interaction, String userMessage, Throwable error) {
        logFailure(userMessage, error);
        interaction.reply(userMessage).queue();
    }

    /** Logs a failure the user hears about some other way, such as a DM. */
    public static void logFailure(String context, Throwable error) {
        log.error(context, error);
    }

    /**
     * As {@link #finish}, but a failure is sent as a private follow-up and the original message is
     * left alone. For buttons on a message that has to stay as it is, such as a review request.
     */
    public static <T> CompletableFuture<Void> finishWithFollowUp(CompletableFuture<T> work, InteractionHook hook, String action,
                                                                 String failureMessage, Consumer<T> onSuccess) {
        return work.thenAccept(onSuccess).exceptionally(error -> {
            log.error("Failed to {}", action, error);
            hook.sendMessage(failureMessage).setEphemeral(true).queue();
            return null;
        });
    }

    /**
     * Runs {@code onSuccess} with the result. If the work fails, or {@code onSuccess} throws, the
     * error is logged against {@code action} and the deferred reply is replaced with
     * {@code failureMessage}.
     *
     * @param action what was being done, for the log, e.g. "save reminder 1234"
     * @return completes once the reply has been handled, for tests
     */
    public static <T> CompletableFuture<Void> finish(CompletableFuture<T> work, InteractionHook hook, String action,
                                                     String failureMessage, Consumer<T> onSuccess) {
        return work.thenAccept(onSuccess).exceptionally(error -> {
            log.error("Failed to {}", action, error);
            hook.editOriginal(failureMessage).setComponents().setEmbeds().queue();
            return null;
        });
    }
}
