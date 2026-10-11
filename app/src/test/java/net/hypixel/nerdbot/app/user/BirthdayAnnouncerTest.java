package net.hypixel.nerdbot.app.user;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.birthday.BirthdayData;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirthdayAnnouncerTest {

    private static DiscordUser member(String id, LocalDate birthDate, String timezone) {
        DiscordUser user = new DiscordUser(id);
        user.getBirthdayData().setBirthDate(birthDate);
        user.getBirthdayData().setTimezone(timezone);
        return user;
    }

    private static BirthdayData data(LocalDate birthDate, String timezone) {
        return member("x", birthDate, timezone).getBirthdayData();
    }

    @Test
    void dueOnlyOnTheDateInTheMembersZone() {
        BirthdayData utc = data(LocalDate.of(2007, 10, 12), "UTC");
        assertFalse(BirthdayAnnouncer.isDue(utc, Instant.parse("2026-10-11T23:59:59Z")));
        assertTrue(BirthdayAnnouncer.isDue(utc, Instant.parse("2026-10-12T00:00:00Z")));
        assertTrue(BirthdayAnnouncer.isDue(utc, Instant.parse("2026-10-12T23:59:59Z")));
        assertFalse(BirthdayAnnouncer.isDue(utc, Instant.parse("2026-10-13T00:00:00Z")));
    }

    @Test
    void extremeZonesShiftTheWindow() {
        BirthdayData plus14 = data(LocalDate.of(2007, 10, 12), "Pacific/Kiritimati");
        assertTrue(BirthdayAnnouncer.isDue(plus14, Instant.parse("2026-10-11T10:00:00Z")));
        assertFalse(BirthdayAnnouncer.isDue(plus14, Instant.parse("2026-10-12T10:00:00Z")));

        BirthdayData minus12 = data(LocalDate.of(2007, 10, 12), "Etc/GMT+12");
        assertFalse(BirthdayAnnouncer.isDue(minus12, Instant.parse("2026-10-12T11:59:59Z")));
        assertTrue(BirthdayAnnouncer.isDue(minus12, Instant.parse("2026-10-12T12:00:00Z")));
    }

    @Test
    void notDueOnceAnnouncedThisYearButDueAgainNextYear() {
        BirthdayData data = data(LocalDate.of(2007, 10, 12), "UTC");
        data.setLastAnnouncedYear(2026);
        assertFalse(BirthdayAnnouncer.isDue(data, Instant.parse("2026-10-12T05:00:00Z")));
        assertTrue(BirthdayAnnouncer.isDue(data, Instant.parse("2027-10-12T05:00:00Z")));
    }

    @Test
    void leaplingsAreDueOnFebruary28thInNonLeapYears() {
        BirthdayData leapling = data(LocalDate.of(2008, 2, 29), "UTC");
        assertTrue(BirthdayAnnouncer.isDue(leapling, Instant.parse("2027-02-28T05:00:00Z")));
        assertFalse(BirthdayAnnouncer.isDue(leapling, Instant.parse("2027-03-01T05:00:00Z")));
        assertFalse(BirthdayAnnouncer.isDue(leapling, Instant.parse("2028-02-28T05:00:00Z")));
        assertTrue(BirthdayAnnouncer.isDue(leapling, Instant.parse("2028-02-29T05:00:00Z")));
    }

    @Test
    void unsetOrMissingDataIsNeverDue() {
        assertFalse(BirthdayAnnouncer.isDue(null, Instant.parse("2026-10-12T05:00:00Z")));
        assertFalse(BirthdayAnnouncer.isDue(new BirthdayData(), Instant.parse("2026-10-12T05:00:00Z")));
    }

    @Test
    void messageMentionsTheMemberAndOptionallyTheirAge() {
        DiscordUser user = member("42", LocalDate.of(2007, 10, 4), "UTC");
        assertEquals("Happy birthday <@42>!", BirthdayAnnouncer.message(user, LocalDate.of(2026, 10, 4)));

        user.getBirthdayData().setShouldAnnounceAge(true);
        assertEquals("Happy birthday <@42>! You are now 19 years old!",
            BirthdayAnnouncer.message(user, LocalDate.of(2026, 10, 4)));

        DiscordUser leapling = member("7", LocalDate.of(2008, 2, 29), "UTC");
        leapling.getBirthdayData().setShouldAnnounceAge(true);
        assertEquals("Happy birthday <@7>! You are now 19 years old!",
            BirthdayAnnouncer.message(leapling, LocalDate.of(2027, 2, 28)));
    }

    private final List<String> sent = new ArrayList<>();
    private final Clock onTheDay = Clock.fixed(Instant.parse("2026-10-12T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void recordsAndSavesBeforeSending() {
        DiscordUser user = member("1", LocalDate.of(2007, 10, 12), "UTC");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, message -> {
            assertEquals(2026, user.getBirthdayData().getLastAnnouncedYear());
            assertEquals(List.of("1"), store.savedIds());
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, onTheDay);

        assertEquals(1, announcer.announceDue());
        assertEquals(List.of("Happy birthday <@1>!"), sent);
        assertEquals(0, announcer.announceDue());
    }

    @Test
    void rejectedMessageUndoesTheRecordSoTheNextRunRetries() {
        DiscordUser user = member("1", LocalDate.of(2007, 10, 12), "UTC");
        user.getBirthdayData().setLastAnnouncedYear(2025);
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store,
            message -> CompletableFuture.failedFuture(new IllegalStateException("discord down")), onTheDay);

        announcer.announceDue();

        assertEquals(2025, user.getBirthdayData().getLastAnnouncedYear());
        assertEquals(List.of("1", "1"), store.savedIds());
        assertTrue(BirthdayAnnouncer.isDue(user.getBirthdayData(), onTheDay.instant()));
    }

    @Test
    void failedRecordMeansNothingIsSent() {
        DiscordUser user = member("1", LocalDate.of(2007, 10, 12), "UTC");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user).failSavesFor("1");
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, onTheDay);

        assertEquals(0, announcer.announceDue());
        assertTrue(sent.isEmpty());
        assertEquals(0, user.getBirthdayData().getLastAnnouncedYear());
    }

    @Test
    void oneFailingMemberDoesNotStopTheOthers() {
        FakeDiscordUserStore store = new FakeDiscordUserStore()
            .seed(member("1", LocalDate.of(2007, 10, 12), "UTC"))
            .seed(member("2", LocalDate.of(2001, 10, 12), "UTC"))
            .failSavesFor("1");
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, onTheDay);

        assertEquals(1, announcer.announceDue());
        assertEquals(List.of("Happy birthday <@2>!"), sent);
    }

    @Test
    void settingTheBirthdayOnTheDayItselfStillAnnouncesIt() {
        DiscordUser user = new DiscordUser("1");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, onTheDay);
        assertEquals(0, announcer.announceDue());

        user.setBirthday(LocalDate.of(2007, 10, 12), LocalDate.of(2026, 10, 12));

        assertEquals(1, announcer.announceDue());
    }

    @Test
    void removingAndReAddingTheBirthdayDoesNotAnnounceTwice() {
        DiscordUser user = member("1", LocalDate.of(2007, 10, 12), "UTC");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, onTheDay);
        announcer.announceDue();

        user.getBirthdayData().clearBirthday();
        user.setBirthday(LocalDate.of(2007, 10, 12), LocalDate.of(2026, 10, 12));
        announcer.announceDue();

        assertEquals(1, sent.size());
    }

    @Test
    void changingTimezoneAfterTheAnnouncementDoesNotAnnounceTwice() {
        DiscordUser user = member("1", LocalDate.of(2007, 10, 12), "Pacific/Kiritimati");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(user);
        Clock early = Clock.fixed(Instant.parse("2026-10-11T11:00:00Z"), ZoneOffset.UTC);
        new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, early).announceDue();

        user.getBirthdayData().setTimezone("Etc/GMT+12");
        new BirthdayAnnouncer(store, message -> {
            sent.add(message);
            return CompletableFuture.completedFuture(null);
        }, Clock.fixed(Instant.parse("2026-10-12T13:00:00Z"), ZoneOffset.UTC)).announceDue();

        assertEquals(1, sent.size());
    }
}