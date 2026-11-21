package net.hypixel.nerdbot.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Commands are only handled in the configured guild; see {@link SkyBlockNerdsBot#servesGuild}. */
class MainGuildTest {

    @Test
    void onlyTheConfiguredGuildIsServed() {
        assertTrue(SkyBlockNerdsBot.isMainGuild("1", "1"));
        assertFalse(SkyBlockNerdsBot.isMainGuild("2", "1"));
    }

    @Test
    void noGuildIsServedWhileNoneIsConfigured() {
        assertFalse(SkyBlockNerdsBot.isMainGuild("1", ""));
        assertFalse(SkyBlockNerdsBot.isMainGuild("1", " "));
        assertFalse(SkyBlockNerdsBot.isMainGuild("", ""));
        assertFalse(SkyBlockNerdsBot.isMainGuild("1", null));
    }
}
