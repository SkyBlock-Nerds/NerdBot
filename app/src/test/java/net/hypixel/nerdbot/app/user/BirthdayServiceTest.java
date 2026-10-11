package net.hypixel.nerdbot.app.user;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirthdayServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);

    private final DiscordUser user = new DiscordUser("1");
    private final FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
    private final BirthdayService service = new BirthdayService(store, CLOCK);

    @Test
    void setsBirthdayAndSavesStraightAway() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "10/04/2007", false, null);

        assertEquals(BirthdayService.Status.SUCCESS, outcome.status());
        assertEquals("Set your birthday to 04 October 2007 (timezone: UTC)!", outcome.message());
        assertEquals(LocalDate.of(2007, 10, 4), user.getBirthdayData().getBirthDate());
        assertEquals(List.of("1"), store.savedIds());
    }

    @Test
    void acceptsSingleDigitMonthAndDay() {
        service.setBirthday("1", "5/2/2001", false, null);

        assertEquals(LocalDate.of(2001, 5, 2), user.getBirthdayData().getBirthDate());
    }

    @Test
    void rejectsImpossibleDatesWithoutSaving() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "02/30/2001", false, null);

        assertEquals(BirthdayService.Status.INVALID_DATE, outcome.status());
        assertEquals("That isn't a valid date. Use MM/DD/YYYY, for example 05/20/2001.", outcome.message());
        assertFalse(user.getBirthdayData().isBirthdaySet());
        assertTrue(store.savedIds().isEmpty());
    }

    @Test
    void rejectsOtherFormats() {
        assertEquals(BirthdayService.Status.INVALID_DATE, service.setBirthday("1", "2001-05-20", false, null).status());
        assertEquals(BirthdayService.Status.INVALID_DATE, service.setBirthday("1", "", false, null).status());
    }

    @Test
    void rejectsFutureDatesWithTheReason() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "10/12/2026", false, null);

        assertEquals(BirthdayService.Status.INVALID_DATE, outcome.status());
        assertEquals("Birthday cannot be in the future.", outcome.message());
        assertTrue(store.savedIds().isEmpty());
    }

    @Test
    void acceptsToday() {
        assertEquals(BirthdayService.Status.SUCCESS, service.setBirthday("1", "10/11/2026", false, null).status());
    }

    @Test
    void rejectsYearsBefore1900WithTheReason() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "12/31/1899", false, null);

        assertEquals("Year cannot be before 1900.", outcome.message());
    }

    @Test
    void storesTheGivenTimezone() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "10/04/2007", false, "Asia/Tokyo");

        assertEquals("Asia/Tokyo", user.getBirthdayData().getTimezone());
        assertEquals("Set your birthday to 04 October 2007 (timezone: Asia/Tokyo)!", outcome.message());
    }

    @Test
    void keepsTheExistingTimezoneWhenNoneIsGiven() {
        user.getBirthdayData().setTimezone("Asia/Tokyo");

        service.setBirthday("1", "10/04/2007", false, "  ");

        assertEquals("Asia/Tokyo", user.getBirthdayData().getTimezone());
    }

    @Test
    void rejectsInvalidTimezoneWithoutSaving() {
        BirthdayService.Outcome outcome = service.setBirthday("1", "10/04/2007", false, "Mars/Olympus");

        assertEquals(BirthdayService.Status.INVALID_TIMEZONE, outcome.status());
        assertFalse(user.getBirthdayData().isBirthdaySet());
        assertTrue(store.savedIds().isEmpty());
    }

    @Test
    void storesTheAnnounceAgeChoice() {
        service.setBirthday("1", "10/04/2007", true, null);
        assertTrue(user.getBirthdayData().isShouldAnnounceAge());

        service.setBirthday("1", "10/04/2007", false, null);
        assertFalse(user.getBirthdayData().isShouldAnnounceAge());
    }

    @Test
    void unknownUserIsReported() {
        assertEquals(BirthdayService.Status.USER_NOT_FOUND, service.setBirthday("2", "10/04/2007", false, null).status());
        assertEquals(BirthdayService.Status.USER_NOT_FOUND, service.setTimezone("2", "UTC").status());
        assertEquals(BirthdayService.Status.USER_NOT_FOUND, service.removeBirthday("2").status());
    }

    @Test
    void userWithoutBirthdayDataCanSetOne() {
        user.setBirthdayData(null);

        assertEquals(BirthdayService.Status.SUCCESS, service.setBirthday("1", "10/04/2007", false, null).status());
    }

    @Test
    void saveFailureIsNotReportedAsSuccess() {
        store.failSavesFor("1");

        assertThrows(CompletionException.class, () -> service.setBirthday("1", "10/04/2007", false, null));
    }

    @Test
    void timezoneNeedsABirthdayFirst() {
        BirthdayService.Outcome outcome = service.setTimezone("1", "Asia/Tokyo");

        assertEquals(BirthdayService.Status.BIRTHDAY_NOT_SET, outcome.status());
        assertEquals("You need to set your birthday first using `/profile birthday set`.", outcome.message());
        assertTrue(store.savedIds().isEmpty());
    }

    @Test
    void timezoneUpdateSavesStraightAway() {
        service.setBirthday("1", "10/04/2007", false, null);

        BirthdayService.Outcome outcome = service.setTimezone("1", "Europe/London");

        assertEquals("Your birthday timezone has been updated to `Europe/London`.", outcome.message());
        assertEquals("Europe/London", user.getBirthdayData().getTimezone());
        assertEquals(List.of("1", "1"), store.savedIds());
    }

    @Test
    void timezoneUpdateRejectsInvalidZones() {
        service.setBirthday("1", "10/04/2007", false, null);

        assertEquals(BirthdayService.Status.INVALID_TIMEZONE, service.setTimezone("1", "Mars/Olympus").status());
    }

    @Test
    void removeClearsTheBirthdayKeepsTheAnnouncedYearAndSaves() {
        service.setBirthday("1", "10/04/2007", false, "Asia/Tokyo");
        user.getBirthdayData().setLastAnnouncedYear(2026);

        BirthdayService.Outcome outcome = service.removeBirthday("1");

        assertEquals("Your birthday has been removed!", outcome.message());
        assertFalse(user.getBirthdayData().isBirthdaySet());
        assertNull(user.getBirthdayData().getTimezone());
        assertEquals(2026, user.getBirthdayData().getLastAnnouncedYear());
        assertEquals(List.of("1", "1"), store.savedIds());
    }

    @Test
    void removeWorksWithoutBirthdayData() {
        user.setBirthdayData(null);

        assertEquals(BirthdayService.Status.SUCCESS, service.removeBirthday("1").status());
    }
}