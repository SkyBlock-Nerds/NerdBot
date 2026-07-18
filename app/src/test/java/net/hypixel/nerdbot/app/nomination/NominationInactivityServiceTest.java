package net.hypixel.nerdbot.app.nomination;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the pure inactivity-warning decision extracted from
 * {@link NominationInactivityService#shouldSendInactivityWarning}. These pin behaviour (including
 * the per-sweep threshold and the deliberately-preserved month-only "already this month" check)
 * ahead of unifying the triplicated sweeps.
 */
class NominationInactivityServiceTest {

    private static final int MEMBER_THRESHOLD = 2;
    private static final int NEW_MEMBER_THRESHOLD = 3;

    @Test
    void warnsWhenBelowThresholdAndNeverWarned() {
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(1, MEMBER_THRESHOLD, null, Month.MARCH));
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, null, Month.MARCH));
    }

    @Test
    void doesNotWarnWhenAtOrAboveThreshold() {
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(2, MEMBER_THRESHOLD, null, Month.MARCH));
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(3, MEMBER_THRESHOLD, null, Month.MARCH));
    }

    @Test
    void respectsTheHigherNewMemberThreshold() {
        // Two requirements met is inactive for new members (threshold 3) but active for members (threshold 2).
        assertTrue(NominationInactivityService.shouldSendInactivityWarning(2, NEW_MEMBER_THRESHOLD, null, Month.MARCH));
        assertFalse(NominationInactivityService.shouldSendInactivityWarning(3, NEW_MEMBER_THRESHOLD, null, Month.MARCH));
    }

    @Test
    void doesNotWarnWhenAlreadyWarnedThisMonth() {
        long now = System.currentTimeMillis();
        Month monthOfNow = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).getMonth();

        assertFalse(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, now, monthOfNow));
    }

    @Test
    void warnsWhenLastWarningWasADifferentMonth() {
        long timestamp = System.currentTimeMillis();
        Month timestampMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
        Month differentMonth = timestampMonth.plus(1);

        assertTrue(NominationInactivityService.shouldSendInactivityWarning(0, MEMBER_THRESHOLD, timestamp, differentMonth));
    }
}
