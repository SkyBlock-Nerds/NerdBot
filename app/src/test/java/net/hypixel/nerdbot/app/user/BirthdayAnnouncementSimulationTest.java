package net.hypixel.nerdbot.app.user;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BirthdayAnnouncementSimulationTest {

    private record Sent(Instant at, String message) {
    }

    private static DiscordUser member(String id, LocalDate birthDate, String timezone) {
        DiscordUser user = new DiscordUser(id);
        user.getBirthdayData().setBirthDate(birthDate);
        user.getBirthdayData().setTimezone(timezone);
        return user;
    }

    private static List<Sent> runHourly(FakeDiscordUserStore store, Instant start, int hours,
                                        Set<String> failFirstSendFor) {
        MutableClock clock = new MutableClock(start);
        List<Sent> sent = new ArrayList<>();
        Set<String> alreadyFailed = new HashSet<>();
        BirthdayAnnouncer.Messenger messenger = message -> {
            String id = message.substring(message.indexOf("<@") + 2, message.indexOf('>'));
            if (failFirstSendFor.contains(id) && alreadyFailed.add(id)) {
                return CompletableFuture.failedFuture(new IllegalStateException("discord hiccup"));
            }
            sent.add(new Sent(clock.instant(), message));
            return CompletableFuture.completedFuture(null);
        };

        BirthdayAnnouncer announcer = new BirthdayAnnouncer(store, messenger, clock);
        for (int hour = 0; hour < hours; hour++) {
            announcer.announceDue();
            clock.advance(Duration.ofHours(1));
        }
        return sent;
    }

    private static long count(List<Sent> sent, String id) {
        return sent.stream().filter(s -> s.message().contains("<@" + id + ">")).count();
    }

    @Test
    void everyoneIsAnnouncedExactlyOnceOnTheirLocalDate() {
        DiscordUser utc = member("utc", LocalDate.of(2000, 10, 12), "UTC");
        DiscordUser kiritimati = member("kiritimati", LocalDate.of(2000, 10, 12), "Pacific/Kiritimati");
        DiscordUser minus12 = member("minus12", LocalDate.of(2000, 10, 12), "Etc/GMT+12");
        DiscordUser flaky = member("flaky", LocalDate.of(2000, 10, 12), "UTC");
        DiscordUser later = member("later", LocalDate.of(2000, 10, 15), "UTC");
        FakeDiscordUserStore store = new FakeDiscordUserStore()
            .seed(utc).seed(kiritimati).seed(minus12).seed(flaky).seed(later);

        List<Sent> sent = runHourly(store, Instant.parse("2026-10-10T00:30:00Z"), 72, Set.of("flaky"));

        for (DiscordUser user : List.of(utc, kiritimati, minus12, flaky)) {
            assertEquals(1, count(sent, user.getDiscordId()), user.getDiscordId());
        }
        assertEquals(0, count(sent, "later"));
        for (Sent s : sent) {
            String id = s.message().substring(s.message().indexOf("<@") + 2, s.message().indexOf('>'));
            ZoneId zone = store.findById(id).orElseThrow().getBirthdayData().getTimeZoneId();
            assertEquals(LocalDate.of(2026, 10, 12), LocalDate.ofInstant(s.at(), zone), id);
        }
    }

    @Test
    void leaplingIsAnnouncedOnceOnFebruary28th() {
        DiscordUser leapling = member("leap", LocalDate.of(2008, 2, 29), "UTC");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(leapling);

        List<Sent> sent = runHourly(store, Instant.parse("2027-02-26T00:30:00Z"), 96, Set.of());

        assertEquals(1, sent.size());
        assertEquals(LocalDate.of(2027, 2, 28), LocalDate.ofInstant(sent.get(0).at(), ZoneId.of("UTC")));
    }

    @Test
    void longDstDayAnnouncesOnce() {
        DiscordUser newYork = member("ny", LocalDate.of(1990, 11, 1), "America/New_York");
        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(newYork);

        List<Sent> sent = runHourly(store, Instant.parse("2026-10-31T00:30:00Z"), 72, Set.of());

        assertEquals(1, sent.size());
        assertEquals(LocalDate.of(2026, 11, 1), LocalDate.ofInstant(sent.get(0).at(), ZoneId.of("America/New_York")));
    }
}