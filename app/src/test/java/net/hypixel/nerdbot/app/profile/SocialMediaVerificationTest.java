package net.hypixel.nerdbot.app.profile;

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
        Optional<String> error = MojangProfileLookup.socialVerificationError("Notch", null, "Notch", false);

        assertTrue(error.isPresent());
        assertEquals("The Hypixel profile for `Notch` does not have any social media linked!", error.get());
    }

    @Test
    void passesWhenLinkedDiscordMatchesCaseInsensitively() {
        assertTrue(MojangProfileLookup.socialVerificationError("Notch", "notch", "Notch", true).isEmpty());
        assertTrue(MojangProfileLookup.socialVerificationError("Notch", "Notch", "Notch", true).isEmpty());
    }

    @Test
    void failsWhenLinkedDiscordDoesNotMatch() {
        Optional<String> error = MojangProfileLookup.socialVerificationError("Notch", "Herobrine", "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `Herobrine`", error.get());
    }

    @Test
    void failsWhenSocialMediaIsPresentButNoDiscordLinkIsSet() {
        Optional<String> error = MojangProfileLookup.socialVerificationError("Notch", null, "Notch", true);

        assertTrue(error.isPresent());
        assertEquals("The Hypixel profile for `Notch` does not have a Discord account linked!", error.get());
    }
}
