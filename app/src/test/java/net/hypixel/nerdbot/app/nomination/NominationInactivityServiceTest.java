package net.hypixel.nerdbot.app.nomination;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure inactivity-warning decision
 * {@link NominationInactivityService#shouldSendInactivityWarning}, including the per-sweep threshold
 * and the "already warned this calendar month" check.
 */
class NominationInactivityServiceTest {

    private static final int MEMBER_THRESHOLD = 2;
    private static final int NEW_MEMBER_THRESHOLD = 3;
    private static final YearMonth MARCH_2026 = YearMonth.of(2026, 3);

    @Test
    void warnsWhenBelowThresholdAndNeverWarned() {
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(1, MEMBER_THRESHOLD, null, MARCH_2026));
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, null, MARCH_2026));
    }

    @Test
    void doesNotWarnWhenAtOrAboveThreshold() {
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(2, MEMBER_THRESHOLD, null, MARCH_2026));
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(3, MEMBER_THRESHOLD, null, MARCH_2026));
    }

    @Test
    void respectsTheHigherNewMemberThreshold() {
        // Two requirements met is inactive for new members (threshold 3) but active for members (threshold 2).
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(2, NEW_MEMBER_THRESHOLD, null, MARCH_2026));
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(3, NEW_MEMBER_THRESHOLD, null, MARCH_2026));
    }

    @Test
    void doesNotWarnWhenAlreadyWarnedThisMonth() {
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, epochMillisAt(2026, 3, 10), MARCH_2026));
    }

    @Test
    void warnsWhenLastWarningWasAnEarlierMonthOfTheSameYear() {
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, epochMillisAt(2026, 1, 10), MARCH_2026));
    }

    @Test
    void warnsWhenLastWarningWasTheSameMonthOfAPreviousYear() {
        // Regression: "already this month" must consider the year, not just the calendar month.
        // Previously March 2025 was wrongly treated as the current March and suppressed the warning.
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, epochMillisAt(2025, 3, 10), MARCH_2026));
    }

    private static long epochMillisAt(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
