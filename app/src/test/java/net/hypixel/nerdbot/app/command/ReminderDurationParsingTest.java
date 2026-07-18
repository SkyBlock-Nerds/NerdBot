package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ReminderCommands#parseDuration}, the {@code 1w2d3h4m5s} duration parser.
 */
class ReminderDurationParsingTest {

    @Test
    void expandsWeeksToSevenDays() {
        assertEquals(Duration.ofDays(7), ReminderCommands.parseDuration("1w"));
    }

    @Test
    void parsesEachUnit() {
        assertEquals(Duration.ofDays(2), ReminderCommands.parseDuration("2d"));
        assertEquals(Duration.ofHours(3), ReminderCommands.parseDuration("3h"));
        assertEquals(Duration.ofMinutes(4), ReminderCommands.parseDuration("4m"));
        assertEquals(Duration.ofSeconds(5), ReminderCommands.parseDuration("5s"));
    }

    @Test
    void accumulatesMultipleUnits() {
        Duration expected = Duration.ofDays(9).plusHours(3).plusMinutes(4).plusSeconds(5); // 1w + 2d
        assertEquals(expected, ReminderCommands.parseDuration("1w2d3h4m5s"));
    }

    @Test
    void emptyStringYieldsZero() {
        assertEquals(Duration.ZERO, ReminderCommands.parseDuration(""));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(DateTimeParseException.class, () -> ReminderCommands.parseDuration("abc"));
    }
}
