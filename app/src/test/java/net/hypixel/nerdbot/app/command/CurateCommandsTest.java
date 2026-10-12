package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.app.curator.Curation;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurateCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    private final ForumChannel forum = Fakes.of(ForumChannel.class, Map.of());
    private final List<Curation> curations = new ArrayList<>();
    private final List<Runnable> queued = new ArrayList<>();
    private int greenlit;
    private boolean busy;
    private RuntimeException failure;

    private final CurateCommands commands = new CurateCommands((channel, curation) -> {
        curations.add(curation);
        if (failure != null) {
            throw failure;
        }
        if (busy) {
            return false;
        }
        curation.start(1_000, greenlit);
        for (int i = 0; i < greenlit; i++) {
            curation.addGreenlit(new GreenlitMessage());
        }
        curation.finish(1_250);
        return true;
    }, queued::add);

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void theReplyIsDeferredBeforeTheCuratorRuns() {
        commands.run(DiscordFakes.slash(recorder, "1"), forum, true);

        assertTrue(recorder.called("deferReply"));
        assertEquals(1, queued.size());
        assertTrue(curations.isEmpty());
        assertFalse(recorder.called("editOriginal"));
    }

    @Test
    void aReadOnlyRequestIsADryRun() {
        commands.run(DiscordFakes.slash(recorder, "1"), forum, true);
        queued.getFirst().run();

        assertTrue(curations.getFirst().isDryRun());
    }

    @Test
    void greenlitSuggestionsAreCounted() {
        greenlit = 2;

        commands.run(DiscordFakes.slash(recorder, "1"), forum, false);
        queued.getFirst().run();

        assertFalse(curations.getFirst().isDryRun());
        assertEquals("Greenlit 2 suggestions in 250ms!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void nothingGreenlitSaysSo() {
        commands.run(DiscordFakes.slash(recorder, "1"), forum, false);
        queued.getFirst().run();

        assertEquals("No suggestions were greenlit", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aCurationAlreadyRunningSaysSo() {
        busy = true;

        commands.run(DiscordFakes.slash(recorder, "1"), forum, false);
        queued.getFirst().run();

        assertEquals(CurateCommands.BUSY_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aCuratorThatThrowsGetsTheFailureMessage() {
        failure = new IllegalStateException("forum vanished");

        commands.run(DiscordFakes.slash(recorder, "1"), forum, false);
        queued.getFirst().run();

        assertEquals("Curation failed. Please try again later.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void progressShowsWhereTheCuratorIs() {
        assertEquals("Curation progress: 3/10 in 2s\nCurrently looking at https://discord.com/x",
            CurateCommands.progressMessage(3, 10, 2_000, "https://discord.com/x"));
    }
}
