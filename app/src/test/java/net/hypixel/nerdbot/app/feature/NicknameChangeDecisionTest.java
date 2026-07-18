package net.hypixel.nerdbot.app.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deciding whether a member's nickname needs updating: only when the current name does not already
 * contain the Mojang username (case-insensitive).
 */
class NicknameChangeDecisionTest {

    @Test
    void nicknameUpdateNeededWhenCurrentNameLacksUsername() {
        assertTrue(ProfileUpdateFeature.needsNicknameUpdate("SomeNickname", "Notch"));
    }

    @Test
    void nicknameUpdateNotNeededWhenCurrentNameAlreadyContainsUsernameCaseInsensitively() {
        assertFalse(ProfileUpdateFeature.needsNicknameUpdate("xX_NOTCH_Xx", "notch"));
        assertFalse(ProfileUpdateFeature.needsNicknameUpdate("Notch", "Notch"));
    }
}
