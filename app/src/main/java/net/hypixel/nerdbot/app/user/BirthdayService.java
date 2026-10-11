package net.hypixel.nerdbot.app.user;

import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.birthday.BirthdayData;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Optional;

public class BirthdayService {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("M/d/uuuu")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter REPLY_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    public enum Status {
        SUCCESS, USER_NOT_FOUND, INVALID_DATE, INVALID_TIMEZONE, BIRTHDAY_NOT_SET
    }

    public record Outcome(Status status, String message) {
    }

    private static final Outcome USER_NOT_FOUND = new Outcome(Status.USER_NOT_FOUND, "User not found");
    private static final Outcome BAD_FORMAT = new Outcome(Status.INVALID_DATE,
        "That isn't a valid date. Use MM/DD/YYYY, for example 05/20/2001.");

    private final DiscordUserStore store;
    private final Clock clock;

    public BirthdayService(DiscordUserStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Outcome setBirthday(String discordId, String input, boolean announceAge, String timezone) {
        Optional<ZoneId> zone = Optional.empty();
        if (timezone != null && !timezone.isBlank()) {
            zone = parseZone(timezone);
            if (zone.isEmpty()) {
                return invalidTimezone(timezone);
            }
        }

        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(input.trim(), INPUT_FORMAT);
        } catch (DateTimeParseException exception) {
            return BAD_FORMAT;
        }

        Optional<DiscordUser> found = store.findById(discordId);
        if (found.isEmpty()) {
            return USER_NOT_FOUND;
        }

        DiscordUser user = found.get();
        ZoneId effectiveZone = zone.orElseGet(() -> user.getBirthdayData() == null
            ? ZoneId.of("UTC") : user.getBirthdayData().getTimeZoneId());

        try {
            user.setBirthday(birthDate, LocalDate.now(clock.withZone(effectiveZone)));
        } catch (DateTimeException exception) {
            return new Outcome(Status.INVALID_DATE, exception.getMessage() + ".");
        }

        BirthdayData data = user.getBirthdayData();
        data.setShouldAnnounceAge(announceAge);
        zone.ifPresent(id -> data.setTimezone(id.getId()));
        store.save(user).join();

        return new Outcome(Status.SUCCESS, String.format("Set your birthday to %s (timezone: %s)!",
            REPLY_FORMAT.format(birthDate), data.getTimeZoneId().getId()));
    }

    public Outcome setTimezone(String discordId, String timezone) {
        Optional<ZoneId> zone = parseZone(timezone);
        if (zone.isEmpty()) {
            return invalidTimezone(timezone);
        }

        Optional<DiscordUser> found = store.findById(discordId);
        if (found.isEmpty()) {
            return USER_NOT_FOUND;
        }

        BirthdayData data = found.get().getBirthdayData();
        if (data == null || !data.isBirthdaySet()) {
            return new Outcome(Status.BIRTHDAY_NOT_SET, "You need to set your birthday first using `/profile birthday set`.");
        }

        data.setTimezone(zone.get().getId());
        store.save(found.get()).join();
        return new Outcome(Status.SUCCESS, "Your birthday timezone has been updated to `" + zone.get().getId() + "`.");
    }

    public Outcome removeBirthday(String discordId) {
        Optional<DiscordUser> found = store.findById(discordId);
        if (found.isEmpty()) {
            return USER_NOT_FOUND;
        }

        if (found.get().getBirthdayData() != null) {
            found.get().getBirthdayData().clearBirthday();
        }

        store.save(found.get()).join();
        return new Outcome(Status.SUCCESS, "Your birthday has been removed!");
    }

    private static Optional<ZoneId> parseZone(String timezone) {
        try {
            return Optional.of(ZoneId.of(timezone.trim()));
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
    }

    private static Outcome invalidTimezone(String timezone) {
        return new Outcome(Status.INVALID_TIMEZONE, "Invalid timezone: `" + timezone + "`. Please use a valid timezone ID "
            + "(e.g., `America/New_York`, `Europe/London`, `Asia/Tokyo`, `UTC`).");
    }
}