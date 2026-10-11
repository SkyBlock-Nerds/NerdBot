package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRepliesTest {

    private final CallRecorder recorder = new CallRecorder();
    private final InteractionHook hook = recorder.fake(InteractionHook.class);

    @Test
    void deferPrivatelyAcknowledgesEphemerallyAndReturnsTheHook() {
        SlashCommandInteraction interaction = recorder.fake(SlashCommandInteraction.class);

        InteractionHook returned = CommandReplies.deferPrivately(interaction);

        assertSame(interaction.getHook(), returned);
        assertEquals(true, recorder.last("setEphemeral").arg(0));
        assertTrue(recorder.called("queue"));
    }

    @Test
    void replyPrivatelyIsEphemeral() {
        CommandReplies.replyPrivately(recorder.fake(SlashCommandInteraction.class), "hello");

        assertEquals("hello", recorder.last("reply").arg(0));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    @Test
    void successRunsTheHandlerAndLeavesTheReplyAlone() {
        List<String> handled = new ArrayList<>();

        CommandReplies.finish(CompletableFuture.completedFuture("done"), hook, "test", "failed", handled::add).join();

        assertEquals(List.of("done"), handled);
        assertFalse(recorder.called("editOriginal"));
    }

    @Test
    void failedWorkReplacesTheReplyWithTheFailureMessage() {
        CommandReplies.finish(CompletableFuture.failedFuture(new IllegalStateException("boom")), hook, "test", "Something broke", result -> {
        }).join();

        assertEquals("Something broke", recorder.last("editOriginal").arg(0));
        assertTrue(recorder.called("setComponents"), "stale buttons are removed");
        assertTrue(recorder.called("setEmbeds"), "stale embeds are removed");
    }

    @Test
    void aHandlerThatThrowsAlsoShowsTheFailureMessage() {
        CommandReplies.finish(CompletableFuture.completedFuture("done"), hook, "test", "Something broke", result -> {
            throw new IllegalStateException("handler broke");
        }).join();

        assertEquals("Something broke", recorder.last("editOriginal").arg(0));
    }

    @Test
    void failEditsADeferredReplyWithTheSafeMessage() {
        CommandReplies.fail(hook, "Something broke", new IllegalStateException("secret detail"));

        assertEquals("Something broke", recorder.last("editOriginal").arg(0));
    }

    @Test
    void failRepliesToAnInteractionThatWasNotDeferred() {
        CommandReplies.fail(recorder.fake(SlashCommandInteraction.class), "Something broke", new IllegalStateException("secret detail"));

        assertEquals("Something broke", recorder.last("reply").arg(0));
    }

    @Test
    void workThatFinishesLaterIsStillHandled() {
        CompletableFuture<String> work = new CompletableFuture<>();
        CompletableFuture<Void> finished = CommandReplies.finish(work, hook, "test", "Something broke", result -> {
        });

        assertFalse(finished.isDone());
        work.completeExceptionally(new IllegalStateException("late"));

        finished.join();
        assertEquals("Something broke", recorder.last("editOriginal").arg(0));
    }
}
