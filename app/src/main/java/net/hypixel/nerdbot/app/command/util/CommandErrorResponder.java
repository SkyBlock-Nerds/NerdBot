package net.hypixel.nerdbot.app.command.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

/**
 * Centralises how slash commands report an internal failure. The exception is logged (which the
 * logback Sentry appender forwards to Sentry), while the user only ever sees a generic,
 * caller-supplied message. Internal exception detail ({@link Throwable#getMessage()}, stack traces,
 * filesystem paths) must never be passed as the user message.
 */
@Slf4j
@UtilityClass
public class CommandErrorResponder {

    /**
     * Report a command failure after the interaction has been deferred, editing the original reply.
     *
     * @param hook        the interaction hook whose original reply is edited
     * @param userMessage the generic, user-safe message to display (never the exception message)
     * @param error       the exception to log and capture
     */
    public static void respond(InteractionHook hook, String userMessage, Throwable error) {
        capture(userMessage, error);
        hook.editOriginal(userMessage).queue();
    }

    /**
     * Report a command failure on an interaction that has not been deferred, sending a direct reply.
     *
     * @param event       the interaction to reply to
     * @param userMessage the generic, user-safe message to display (never the exception message)
     * @param error       the exception to log and capture
     */
    public static void respond(IReplyCallback event, String userMessage, Throwable error) {
        capture(userMessage, error);
        event.reply(userMessage).queue();
    }

    /**
     * Log an exception (which the logback Sentry appender forwards to Sentry) without sending a
     * user-facing reply. Use when the user is notified through a channel other than the interaction
     * (e.g. a direct message).
     *
     * @param context describes what failed, used as the log message
     * @param error   the exception to log
     */
    public static void capture(String context, Throwable error) {
        log.error(context, error);
    }
}
