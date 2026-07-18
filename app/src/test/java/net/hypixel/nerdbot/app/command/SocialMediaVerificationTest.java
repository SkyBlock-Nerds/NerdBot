package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifying that a member's Discord username matches the Discord handle linked on their Hypixel
 * profile, including the exact user-facing error messages.
 */
class SocialMediaVerificationTest {

    @Test
    void failsWhenNoSocialMediaIsLinked() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", null, "Notch", false);

        assertTrue(error.isPresent());
        assertEquals("The Hypixel profile for `Notch` does not have any social media linked!", error.get());
    }

    @Test
    void passesWhenLinkedDiscordMatchesCaseInsensitively() {
        assertTrue(ProfileCommands.socialVerificationError("Notch", "notch", "Notch", true).isEmpty());
        assertTrue(ProfileCommands.socialVerificationError("Notch", "Notch", "Notch", true).isEmpty());
    }

    @Test
    void failsWhenLinkedDiscordDoesNotMatch() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", "Herobrine", "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `Herobrine`", error.get());
    }

    @Test
    void failsWhenSocialMediaIsPresentButNoDiscordLinkIsSet() {
        Optional<String> error = ProfileCommands.socialVerificationError("Notch", null, "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `null`", error.get());
    }
}
