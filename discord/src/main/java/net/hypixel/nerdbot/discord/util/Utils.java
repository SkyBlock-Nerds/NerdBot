package net.hypixel.nerdbot.discord.util;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
public class Utils {

    public static final String[] SPECIAL_ROLES = {"Apex Nerd", "Ultimate Nerd", "Ultimate Nerd But Red"};

    private Utils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    @Deprecated
    public static Optional<String> getScuffedMinecraftIGN(Member member) {
        String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
        String memberMCUsername = null;

        if (!Pattern.matches(StringUtils.MINECRAFT_USERNAME_REGEX, plainUsername)) {
            plainUsername = plainUsername.replaceAll(StringUtils.SURROUND_REGEX, "").replace(" ", "");
            String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

            for (String item : splitUsername) {
                if (Pattern.matches(StringUtils.MINECRAFT_USERNAME_REGEX, item)) {
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