package net.hypixel.nerdbot.app.config;

import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigEditorTest {

    private final FakeConfigStore store = new FakeConfigStore(new NerdBotConfig());
    private final ConfigEditor editor = new ConfigEditor(store::current, store);

    @Test
    void aTopLevelNumberIsSavedAndMadeLive() throws IOException {
        NerdBotConfig before = store.current();

        ConfigEditor.Saved saved = assertInstanceOf(ConfigEditor.Saved.class, editor.edit("messageLimit", "250"));

        assertEquals(250, saved.config().getMessageLimit());
        assertSame(saved.config(), store.current());
        assertNotSame(before, store.current());
        assertEquals(1, store.saves());
    }

    @Test
    void aNestedValueIsSaved() throws IOException {
        editor.edit("roleConfig.limboRoleId", "\"123\"");

        assertEquals("123", store.current().getRoleConfig().getLimboRoleId());
    }

    @Test
    void anUnsetValueCanBeSet() throws IOException {
        assertNull(store.current().getFunConfig().getAprilFoolsTimezone());

        editor.edit("funConfig.aprilFoolsTimezone", "\"Europe/London\"");

        assertEquals("Europe/London", store.current().getFunConfig().getAprilFoolsTimezone());
    }

    @Test
    void aListEntryIsSavedByIndex() throws IOException {
        store.current().setFeatures(List.of(new FeatureConfig(), new FeatureConfig()));

        editor.edit("features[1].periodMs", "5000");

        assertEquals(5000, store.current().getFeatures().get(1).getPeriodMs());
    }

    @Test
    void unquotedSingleWordsAreReadAsText() throws IOException {
        editor.edit("roleConfig.limboRoleId", "456");

        assertEquals("456", store.current().getRoleConfig().getLimboRoleId());
    }

    @Test
    void aTypoInTheLastKeyIsRejected() throws IOException {
        assertRejected("messageLimt", "5", "There's no `messageLimt` in the config.");
    }

    @Test
    void aTypoPartWayIsRejected() throws IOException {
        assertRejected("roleConfg.limboRoleId", "\"1\"", "There's no `roleConfg` in the config.");
    }

    @Test
    void indexingSomethingThatIsntAListIsRejected() throws IOException {
        assertRejected("roleConfig[0].limboRoleId", "\"1\"", "`roleConfig` isn't a list, so it can't take an index.");
    }

    @Test
    void anIndexPastTheEndIsRejected() throws IOException {
        store.current().setFeatures(List.of(new FeatureConfig()));

        assertRejected("features[3].periodMs", "1", "`features` only has 1 entries.");
    }

    @Test
    void aValueOfTheWrongTypeIsRejectedSoTheBotStillStarts() throws IOException {
        ConfigEditor.Rejected rejected = assertInstanceOf(ConfigEditor.Rejected.class, editor.edit("messageLimit", "\"lots\""));

        assertTrue(rejected.reason().startsWith("`\"lots\"` doesn't fit `messageLimit`"), rejected.reason());
        assertEquals(0, store.saves());
        assertEquals(100, store.current().getMessageLimit());
    }

    @Test
    void anObjectWhereANumberBelongsIsRejected() throws IOException {
        assertInstanceOf(ConfigEditor.Rejected.class, editor.edit("messageLimit", "{\"a\":1}"));
        assertEquals(0, store.saves());
    }

    @Test
    void invalidJsonIsExplained() throws IOException {
        assertRejected("roleConfig.limboRoleId", "hello world", "`hello world` isn't valid JSON. Put text in quotes, for example \"hello world\".");
    }

    @Test
    void blockedKeysAreRefusedBeforeAnythingElse() throws IOException {
        assertRejected("guildId", "\"1\"", "Editing the `guildId` config key is not permitted.");
        assertRejected("ownerIds[0]", "\"1\"", "Editing the `ownerIds[0]` config key is not permitted.");
    }

    @Test
    void aFailedWriteLeavesTheLiveConfigAlone() {
        NerdBotConfig before = store.current();
        store.failWrites();

        assertThrows(IOException.class, () -> editor.edit("messageLimit", "250"));
        assertSame(before, store.current());
    }

    private void assertRejected(String key, String value, String reason) throws IOException {
        assertEquals(new ConfigEditor.Rejected(reason), editor.edit(key, value));
        assertEquals(0, store.saves());
    }
}
