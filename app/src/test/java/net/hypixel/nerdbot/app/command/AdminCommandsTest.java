package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the {@code /config edit} guard extracted from {@link AdminCommands}.
 */
class AdminCommandsTest {

    @Test
    void blocksSensitiveTopLevelKeys() {
        assertTrue(AdminCommands.isBlockedConfigKey("token"));
        assertTrue(AdminCommands.isBlockedConfigKey("ownerIds"));
        assertTrue(AdminCommands.isBlockedConfigKey("guildId"));
        assertTrue(AdminCommands.isBlockedConfigKey("databaseUri"));
        assertTrue(AdminCommands.isBlockedConfigKey("databaseName"));
    }

    @Test
    void blocksNestedPathsBeneathSensitiveKeys() {
        assertTrue(AdminCommands.isBlockedConfigKey("token.value"));
        assertTrue(AdminCommands.isBlockedConfigKey("databaseUri.host.port"));
    }

    @Test
    void allowsNonSensitiveKeys() {
        assertFalse(AdminCommands.isBlockedConfigKey("messageLimit"));
        assertFalse(AdminCommands.isBlockedConfigKey("roleConfig.memberRoleId"));
        assertFalse(AdminCommands.isBlockedConfigKey("mojangUsernameCacheTTL"));
    }

    @Test
    void rootConfigKeyReturnsSegmentBeforeFirstDot() {
        assertEquals("roleConfig", AdminCommands.rootConfigKey("roleConfig.memberRoleId"));
        assertEquals("a", AdminCommands.rootConfigKey("a.b.c"));
        assertEquals("token", AdminCommands.rootConfigKey("token"));
    }
}
