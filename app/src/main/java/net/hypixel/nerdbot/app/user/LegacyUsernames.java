package net.hypixel.nerdbot.app.user;

import net.dv8tion.jda.api.entities.Member;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Guesses a Minecraft username from a member's display name. Only used to migrate members from
 * before profile verification existed; anything new should use the verified Mojang profile.
 */
public final class LegacyUsernames {

    private static final String MINECRAFT_USERNAME_REGEX = "^[a-zA-Z0-9_]{2,16}";
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    private LegacyUsernames() {
    }

    @Deprecated
    public static Optional<String> fromDisplayName(Member member) {
        String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
        String memberMCUsername = null;

        if (!Pattern.matches(MINECRAFT_USERNAME_REGEX, plainUsername)) {
            plainUsername = plainUsername.replaceAll(SURROUND_REGEX, "").replace(" ", "");
            String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

            for (String item : splitUsername) {
                if (Pattern.matches(MINECRAFT_USERNAME_REGEX, item)) {
                    memberMCUsername = item;
                    break;
                }
            }
        } else {
            memberMCUsername = plainUsername.replace(" ", "");
        }

        return Optional.ofNullable(memberMCUsername);
    }
}
