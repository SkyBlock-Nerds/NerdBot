package net.hypixel.nerdbot.app.command;

import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    // No URI means no connection, so no repositories are registered
    private final List<Runnable> queued = new ArrayList<>();
    private final CacheCommands commands = new CacheCommands(new Database(null, null), queued::add);

    @Test
    void anUnknownRepositoryIsNamedAsSuch() {
        commands.withRepository(DiscordFakes.slash(recorder, "1"), "nope", "save", "failed", repository -> "done").join();

        assertEquals(CacheCommands.UNKNOWN_REPOSITORY_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertTrue(queued.isEmpty());
    }
}
