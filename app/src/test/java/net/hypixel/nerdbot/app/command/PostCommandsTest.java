package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.hypixel.nerdbot.app.post.MarkdownPostService;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostCommandsTest {

    // Over Discord's 2,000 character limit, so it is split at the headings
    private static final String TWO_SECTIONS = "# One\n" + "first ".repeat(250) + "\n\n# Two\n" + "second ".repeat(250);

    private final CallRecorder recorder = new CallRecorder();
    private final InteractionHook hook = recorder.fake(InteractionHook.class);
    private final List<String> posted = new ArrayList<>();
    private final PostCommands commands = new PostCommands(new MarkdownPostService());

    @ParameterizedTest
    @ValueSource(strings = {"notes.md", "NOTES.MD", "notes.markdown", "notes.txt"})
    void markdownAndTextFilesAreAccepted(String fileName) {
        assertEquals(Optional.empty(), PostCommands.rejection(fileName, 10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.pdf", "notes", "notes.md.exe"})
    void otherFilesAreRefused(String fileName) {
        assertEquals(Optional.of("Please upload a `.md`, `.markdown`, or `.txt` file."), PostCommands.rejection(fileName, 10));
    }

    @Test
    void theSizeLimitIsInclusive() {
        assertTrue(PostCommands.rejection("notes.md", PostCommands.MAX_ATTACHMENT_SIZE_BYTES).isEmpty());
        assertTrue(PostCommands.rejection("notes.md", PostCommands.MAX_ATTACHMENT_SIZE_BYTES + 1L).orElseThrow().startsWith("Attachment is too large!"));
    }

    @Test
    void eachSectionIsPostedInOrderThenReported() {
        commands.post(hook, channel(true), "notes.md", CompletableFuture.completedFuture(TWO_SECTIONS));

        assertEquals(2, posted.size());
        assertTrue(posted.get(0).contains("first"));
        assertTrue(posted.get(1).contains("second"));
        assertEquals("Posted 2 message(s) from `notes.md`.", lastEdit());
    }

    @Test
    void anEmptyFilePostsNothing() {
        commands.post(hook, channel(true), "notes.md", CompletableFuture.completedFuture("   \n "));

        assertTrue(posted.isEmpty());
        assertEquals("The uploaded file is empty!", lastEdit());
    }

    @Test
    void aFailedDownloadIsReported() {
        commands.post(hook, channel(true), "notes.md", CompletableFuture.failedFuture(new IllegalStateException("404")));

        assertTrue(posted.isEmpty());
        assertTrue(lastEdit().startsWith("An error occurred while reading or posting the file."));
    }

    @Test
    void aFailedSendStopsAndIsReported() {
        commands.post(hook, channel(false), "notes.md", CompletableFuture.completedFuture(TWO_SECTIONS));

        assertEquals(1, posted.size(), "the second section isn't attempted after the first fails");
        assertTrue(lastEdit().contains("some sections may have been posted"));
    }

    private MessageChannel channel(boolean sendsSucceed) {
        return recorder.fake(MessageChannel.class, Map.of("sendMessage", (CallRecorder.Answer) args -> {
            posted.add(args[0].toString());
            CompletableFuture<Object> sent = sendsSucceed
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new IllegalStateException("missing permission"));
            return Fakes.of(MessageCreateAction.class, Map.of("submit", sent));
        }));
    }

    private String lastEdit() {
        return (String) recorder.last("editOriginal").arg(0);
    }
}
