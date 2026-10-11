package net.hypixel.nerdbot.app.config;

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
        assertTrue(ConfigEditor.isBlocked("token"));
        assertTrue(ConfigEditor.isBlocked("ownerIds"));
        assertTrue(ConfigEditor.isBlocked("guildId"));
        assertTrue(ConfigEditor.isBlocked("databaseUri"));
        assertTrue(ConfigEditor.isBlocked("databaseName"));
    }

    @Test
    void blocksNestedPathsBeneathSensitiveKeys() {
        assertTrue(ConfigEditor.isBlocked("token.value"));
        assertTrue(ConfigEditor.isBlocked("databaseUri.host.port"));
    }

    @Test
    void allowsNonSensitiveKeys() {
        assertFalse(ConfigEditor.isBlocked("messageLimit"));
        assertFalse(ConfigEditor.isBlocked("roleConfig.memberRoleId"));
        assertFalse(ConfigEditor.isBlocked("mojangUsernameCacheTTL"));
    }

}
