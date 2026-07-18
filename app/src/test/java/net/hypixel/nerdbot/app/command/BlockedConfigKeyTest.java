package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code /config edit} guard blocks editing sensitive keys - credentials and identity - and
 * every dotted path beneath them.
 */
class BlockedConfigKeyTest {

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

}
