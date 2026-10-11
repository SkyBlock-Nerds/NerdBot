package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.config.ConfigEditor;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    private final FakeConfigStore store = new FakeConfigStore(new NerdBotConfig());
    private final List<Boolean> metrics = new ArrayList<>();
    private final Presence presence = recorder.fake(Presence.class);
    private final ConfigCommands commands = new ConfigCommands(store::current, store, new ConfigEditor(store::current, store), metrics::add,
        Fakes.of(JDA.class, Map.of("getPresence", presence)));

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void theLiveConfigIsSentAsJson() throws IOException {
        store.current().setMessageLimit(42);

        commands.show(DiscordFakes.slash(recorder, "1"));

        FileUpload upload = DiscordFakes.lastCallItems(recorder, "editOriginalAttachments", FileUpload.class).getFirst();
        assertEquals("config.json", upload.getName());
        assertTrue(new String(upload.getData().readAllBytes(), StandardCharsets.UTF_8).contains("\"messageLimit\": 42"));
    }

    @Test
    void aReloadSwapsInTheFileAndAppliesIt() {
        NerdBotConfig onDisk = new NerdBotConfig();
        onDisk.setActivity("the new config");
        onDisk.setActivityType(DiscordBotConfig.ActivityType.PLAYING);
        onDisk.getMetricsConfig().setEnabled(true);
        store.fileContains(onDisk);

        commands.reload(DiscordFakes.slash(recorder, "1"));

        assertEquals("Reloaded the config file!", DiscordFakes.lastEditText(recorder));
        Activity activity = (Activity) recorder.last("setActivity").arg(0);
        assertEquals("the new config", activity.getName());
        assertEquals(Activity.ActivityType.PLAYING, activity.getType());
        assertEquals(List.of(true), metrics);
    }

    @Test
    void aFileThatWontLoadKeepsTheOldConfig() {
        store.failReloads();

        commands.reload(DiscordFakes.slash(recorder, "1"));

        assertEquals("The config file couldn't be loaded, so the old config is still in use. Check the logs for details.", DiscordFakes.lastEditText(recorder));
        assertFalse(recorder.called("setActivity"));
    }

    @Test
    void anEditIsSavedAndMadeLive() {
        commands.edit(DiscordFakes.slash(recorder, "1"), "messageLimit", "250");

        assertEquals("Updated `messageLimit`. The change is live and saved.", DiscordFakes.lastEditText(recorder));
        assertEquals(250, store.current().getMessageLimit());
        assertTrue(recorder.called("setActivity"));
    }

    @Test
    void aRejectedEditSaysWhy() {
        commands.edit(DiscordFakes.slash(recorder, "1"), "messageLimt", "250");

        assertEquals("There's no `messageLimt` in the config.", DiscordFakes.lastEditText(recorder));
        assertEquals(0, store.saves());
    }

    @Test
    void anEditThatCantBeWrittenSaysNothingChanged() {
        store.failWrites();

        commands.edit(DiscordFakes.slash(recorder, "1"), "messageLimit", "250");

        assertEquals(ConfigCommands.SAVE_FAILED_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertEquals(100, store.current().getMessageLimit());
    }

    @Test
    void togglingMetricsSavesTheChange() {
        commands.toggle(DiscordFakes.slash(recorder, "1"));

        assertTrue(store.current().getMetricsConfig().isEnabled());
        assertEquals(1, store.saves());
        assertEquals(List.of(true), metrics);
        assertEquals("Toggled metrics: true", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aToggleThatCantBeSavedIsUndone() {
        store.failWrites();

        commands.toggle(DiscordFakes.slash(recorder, "1"));

        assertFalse(store.current().getMetricsConfig().isEnabled());
        assertEquals(List.of(), metrics);
        assertEquals(ConfigCommands.SAVE_FAILED_MESSAGE, DiscordFakes.lastEditText(recorder));
    }
}
