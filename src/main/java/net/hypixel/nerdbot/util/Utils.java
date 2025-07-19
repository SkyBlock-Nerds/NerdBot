package net.hypixel.nerdbot.util;

import lombok.extern.log4j.Log4j2;

import java.util.Optional;
import java.util.regex.Pattern;

@Log4j2
public class Utils {

    public static final String[] SPECIAL_ROLES = {"Apex Nerd", "Ultimate Nerd", "Ultimate Nerd But Red"};

    private Utils() {
    }


    @Deprecated
    public static Optional<String> getScuffedMinecraftIGN(net.dv8tion.jda.api.entities.Member member) {
        // removes non-standard ascii characters from the discord nickname
        String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
        String memberMCUsername = null;

        // checks if the member's username has flair
        if (!Pattern.matches(StringUtils.MINECRAFT_USERNAME_REGEX, plainUsername)) {
            // removes start and end characters ([example], {example}, |example| or (example)).
            // also strips spaces from the username
            plainUsername = plainUsername.replaceAll(StringUtils.SURROUND_REGEX, "").replace(" ", "");
            String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

            // gets the first item that matches the name constraints
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
