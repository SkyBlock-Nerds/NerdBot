package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.app.nomination.NominationService.NominationOutcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the pure promotion-nomination decision {@link NominationService#decideOutcome}.
 */
class NominationServiceTest {

    private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

    @Test
    void nominatesWhenThresholdsMetAndNeverNominated() {
        assertEquals(NominationOutcome.NOMINATED, NominationService.decideOutcome(2, null, MARCH_2026));
        assertEquals(NominationOutcome.NOMINATED, NominationService.decideOutcome(3, null, MARCH_2026));
    }

    @Test
    void skipsBelowThresholdWhenNeverNominatedAndUnderTwoRequirements() {
        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD, NominationService.decideOutcome(0, null, MARCH_2026));
        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD, NominationService.decideOutcome(1, null, MARCH_2026));
    }

    @Test
    void skipsWhenAlreadyNominatedThisMonthEvenIfEligible() {
        assertEquals(NominationOutcome.SKIPPED_ALREADY_THIS_MONTH,
            NominationService.decideOutcome(3, epochMillisAt(2026, 3, 10), MARCH_2026));
    }

    @Test
    void nominatesWhenLastNominationWasAnEarlierMonthOfTheSameYear() {
        assertEquals(NominationOutcome.NOMINATED,
            NominationService.decideOutcome(2, epochMillisAt(2026, 1, 10), MARCH_2026));
    }

    @Test
    void skipsBelowThresholdWhenDifferentMonthButUnderTwoRequirements() {
        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD,
            NominationService.decideOutcome(1, epochMillisAt(2026, 1, 10), MARCH_2026));
    }

    @Test
    void nominatesWhenLastNominationWasTheSameMonthOfAPreviousYear() {
        // Regression: "already this month" must consider the year, not just the calendar month.
        // Previously March 2025 was wrongly treated as the current March and skipped.
        assertEquals(NominationOutcome.NOMINATED,
            NominationService.decideOutcome(2, epochMillisAt(2025, 3, 10), MARCH_2026));
    }

    private static long epochMillisAt(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
