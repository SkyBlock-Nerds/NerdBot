package net.hypixel.nerdbot.app.command;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdateNotifier;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdateOutcome;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackCommandsTest {

    // Only the PackAction touches the updater, and these tests supply the action
    private static final PackUpdater UPDATER = new PackUpdater(null, null, null, null, null, null, null);

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private Optional<PackUpdater> updater = Optional.of(UPDATER);
    private final PackCommands commands = new PackCommands(() -> updater, () -> config, Runnable::run);

    @Test
    void withoutAnUpdaterNothingRuns() {
        updater = Optional.empty();
        withPacks("hypixel:skyblock");

        commands.showStatus(DiscordFakes.slash(recorder, "1"), null);

        assertEquals("Resource pack auto-update is not configured.", recorder.last("reply").arg(0));
    }

    @Test
    void withoutAnyAutoUpdatedPackNothingRuns() {
        commands.runAsync(DiscordFakes.slash(recorder, "1"), null, "check", (unused, definition) -> {
            throw new AssertionError("should not run");
        });

        assertEquals("Resource pack auto-update is not configured.", recorder.last("reply").arg(0));
    }

    @Test
    void theOnlyPackIsPickedWhenNoneIsNamed() {
        withPacks("hypixel:skyblock");
        List<String> ran = new ArrayList<>();

        commands.runAsync(DiscordFakes.slash(recorder, "1"), null, "check", (unused, definition) -> {
            ran.add(definition.getId());
            return new PackUpdateOutcome.Unavailable("nothing to roll back to");
        });

        assertEquals(List.of("hypixel:skyblock"), ran);
        assertEquals("Not possible: nothing to roll back to", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void withSeveralPacksOneMustBeNamed() {
        withPacks("hypixel:skyblock", "hypixel:dungeons");

        commands.runAsync(DiscordFakes.slash(recorder, "1"), "  ", "check", (unused, definition) -> {
            throw new AssertionError("should not run");
        });

        assertEquals("More than one pack is auto-updated, so choose one with the pack option.", recorder.last("reply").arg(0));
    }

    @Test
    void theNamedPackIsUsedIgnoringCase() {
        withPacks("hypixel:skyblock", "hypixel:dungeons");
        List<String> ran = new ArrayList<>();

        commands.runAsync(DiscordFakes.slash(recorder, "1"), " Hypixel:Dungeons ", "check", (unused, definition) -> {
            ran.add(definition.getId());
            return new PackUpdateOutcome.Busy();
        });

        assertEquals(List.of("hypixel:dungeons"), ran);
    }

    @Test
    void anUnknownPackIsRefused() {
        withPacks("hypixel:skyblock");

        commands.runAsync(DiscordFakes.slash(recorder, "1"), "hypixel:farming", "check", (unused, definition) -> {
            throw new AssertionError("should not run");
        });

        assertEquals("`hypixel:farming` is not an auto-updated pack.", recorder.last("reply").arg(0));
    }

    @Test
    void anActionThatThrowsStillAnswers() {
        withPacks("hypixel:skyblock");

        commands.runAsync(DiscordFakes.slash(recorder, "1"), null, "rollback", (unused, definition) -> {
            throw new IllegalStateException("disk full");
        });

        assertEquals("The pack rollback failed unexpectedly. Check the logs.", DiscordFakes.lastEditText(recorder));
        assertFalse(recorder.called("complete"));
    }

    @Test
    void outcomesAreSummarised() {
        String sha1 = "0123456789abcdef0123456789abcdef01234567";
        String shortSha1 = PackUpdateNotifier.shortHash(sha1);
        assertTrue(shortSha1.length() < sha1.length());

        assertEquals("Already on the latest version (" + shortSha1 + ").", PackCommands.summarize(new PackUpdateOutcome.UpToDate(sha1, false)));
        assertTrue(PackCommands.summarize(new PackUpdateOutcome.UpToDate(sha1, true)).contains("was rejected before"));
        assertEquals("Rejected format 99 (" + shortSha1 + "): too few items", PackCommands.summarize(new PackUpdateOutcome.Rejected(sha1, 99, "too few items")));
        assertEquals("Check failed, will retry on the next scheduled check: timeout", PackCommands.summarize(new PackUpdateOutcome.TransientFailure("timeout", null, 1)));
        assertEquals("A pack check or rollback is already running, try again shortly.", PackCommands.summarize(new PackUpdateOutcome.Busy()));
    }

    @Test
    void longMessagesAreCutToDiscordsLimit() {
        assertEquals(2_000, PackCommands.truncate("x".repeat(2_500)).length());
        assertEquals("short", PackCommands.truncate("short"));
    }

    private void withPacks(String... ids) {
        List<GeneratorConfig.PackDefinition> packs = new ArrayList<>();
        for (String id : ids) {
            GeneratorConfig.PackAutoUpdate autoUpdate = new GeneratorConfig.PackAutoUpdate();
            autoUpdate.setEnabled(true);
            // Each pack needs its own source, or the updater skips both
            autoUpdate.setHypixelPackId(id.substring(id.indexOf(':') + 1));
            autoUpdate.setMinItemRatio(0.5);
            autoUpdate.setSampleItems(List.of("minecraft:diamond_sword"));

            GeneratorConfig.PackDefinition definition = new GeneratorConfig.PackDefinition();
            definition.setId(id);
            definition.setPath("packs/" + id + ".zip");
            definition.setAutoUpdate(autoUpdate);
            packs.add(definition);
        }
        config.getGeneratorConfig().getResourcePacks().setPacks(packs);
    }
}
