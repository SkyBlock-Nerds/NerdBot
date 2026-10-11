package net.hypixel.nerdbot.app.reminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderTimesTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void expandsWeeksToSevenDays() {
        assertEquals(Duration.ofDays(7), ReminderTimes.parseDuration("1w"));
    }

    @Test
    void parsesEachUnitOnItsOwn() {
        assertEquals(Duration.ofDays(2), ReminderTimes.parseDuration("2d"));
        assertEquals(Duration.ofHours(3), ReminderTimes.parseDuration("3h"));
        assertEquals(Duration.ofMinutes(4), ReminderTimes.parseDuration("4m"));
        assertEquals(Duration.ofSeconds(5), ReminderTimes.parseDuration("5s"));
    }

    @Test
    void combinesEveryUnit() {
        Duration expected = Duration.ofDays(9).plusHours(3).plusMinutes(4).plusSeconds(5);
        assertEquals(expected, ReminderTimes.parseDuration("1w2d3h4m5s"));
    }

    @Test
    void emptyStringIsZero() {
        assertEquals(Duration.ZERO, ReminderTimes.parseDuration(""));
    }

    @Test
    void rejectsAnythingElse() {
        assertThrows(DateTimeParseException.class, () -> ReminderTimes.parseDuration("abc"));
        assertThrows(DateTimeParseException.class, () -> ReminderTimes.parseDuration("3h2d"));
    }

    @Test
    void rejectsNumbersTooLargeForALong() {
        assertThrows(DateTimeParseException.class, () -> ReminderTimes.parseDuration("99999999999999999999d"));
    }

    @Test
    void epochMillisAreTakenAsIs() {
        assertEquals(Instant.ofEpochMilli(1_800_000_000_000L), ReminderTimes.parse("1800000000000", CLOCK).orElseThrow());
    }

    @Test
    void durationsCountFromTheClock() {
        assertEquals(NOW.plus(Duration.ofDays(1).plusHours(2)), ReminderTimes.parse("1d2h", CLOCK).orElseThrow());
    }

    @Test
    void naturalLanguageCountsFromTheClock() {
        Instant parsed = ReminderTimes.parse("in 3 days", CLOCK).orElseThrow();

        assertEquals(NOW.plus(Duration.ofDays(3)).getEpochSecond(), parsed.getEpochSecond(), 1);
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals(NOW.plus(Duration.ofHours(2)), ReminderTimes.parse("  2h ", CLOCK).orElseThrow());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "whenever you like", "99999999999999999999d", "9999999999999d"})
    void unparseableInputIsEmpty(String input) {
        assertTrue(ReminderTimes.parse(input, CLOCK).isEmpty());
    }
}
