package net.hypixel.nerdbot.app.reminder;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import java.time.Clock;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns what a user typed for "when" into an instant. */
public final class ReminderTimes {

    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");

    private ReminderTimes() {
    }

    /**
     * Tries, in order: epoch milliseconds, natural language such as "tomorrow at 3pm" (relative to
     * {@code clock}), then a {@code 1w2d3h4m5s} duration from now.
     */
    public static Optional<Instant> parse(String input, Clock clock) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String trimmed = input.trim();

        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(trimmed)));
        } catch (NumberFormatException notEpochMillis) {
            // fall through to the other formats
        }

        Optional<Instant> natural = parseNatural(trimmed, clock);
        if (natural.isPresent()) {
            return natural;
        }

        try {
            return Optional.of(clock.instant().plus(parseDuration(trimmed)));
        } catch (DateTimeException | ArithmeticException notADuration) {
            return Optional.empty();
        }
    }

    /**
     * Parses a {@code 1w2d3h4m5s} duration. Every unit is optional, an empty string is
     * {@link Duration#ZERO}, and a week is seven days.
     *
     * @throws DateTimeParseException if the string isn't in that format
     */
    static Duration parseDuration(String time) {
        Matcher matcher = DURATION.matcher(time);
        if (!matcher.matches()) {
            throw new DateTimeParseException("Could not parse duration: " + time, time, 0);
        }

        Duration duration = Duration.ZERO;
        duration = duration.plusDays(group(matcher, 2) * 7);
        duration = duration.plusDays(group(matcher, 4));
        duration = duration.plusHours(group(matcher, 6));
        duration = duration.plusMinutes(group(matcher, 8));
        return duration.plusSeconds(group(matcher, 10));
    }

    private static long group(Matcher matcher, int group) {
        String value = matcher.group(group);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException tooLarge) {
            throw new DateTimeParseException("Duration value is too large: " + value, matcher.group(), 0);
        }
    }

    // Natty throws a variety of runtime exceptions on input it can't handle, which just means "not natural language"
    private static Optional<Instant> parseNatural(String input, Clock clock) {
        try {
            List<DateGroup> groups = new Parser().parse(input, Date.from(clock.instant()));
            if (groups.isEmpty() || groups.getFirst().getDates().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(groups.getFirst().getDates().getFirst().toInstant());
        } catch (RuntimeException notNaturalLanguage) {
            return Optional.empty();
        }
    }
}
