package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.time.LocalDate;

public final class BirthdayFixtures {

    private BirthdayFixtures() {
    }

    public static DiscordUser member(String id, LocalDate birthDate, String timezone) {
        DiscordUser user = new DiscordUser(id);
        user.getBirthdayData().setBirthDate(birthDate);
        user.getBirthdayData().setTimezone(timezone);
        return user;
    }
}
