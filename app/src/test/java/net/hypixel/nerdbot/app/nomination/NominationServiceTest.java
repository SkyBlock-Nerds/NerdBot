package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.app.nomination.NominationService.NominationOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for the pure nomination decision extracted from
 * {@link NominationService#decideOutcome}. These pin behaviour ahead of any wider refactor of the
 * nomination subsystem, including the deliberately-preserved month-only "already this month" check.
 */
class NominationServiceTest {

    @Test
    void nominatesWhenThresholdsMetAndNeverNominated() {
        assertEquals(NominationOutcome.NOMINATED, NominationService.decideOutcome(2, null, Month.MARCH));
        assertEquals(NominationOutcome.NOMINATED, NominationService.decideOutcome(3, null, Month.MARCH));
    }

    @Test
    void skipsBelowThresholdWhenNeverNominatedAndUnderTwoRequirements() {
        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD, NominationService.decideOutcome(0, null, Month.MARCH));
        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD, NominationService.decideOutcome(1, null, Month.MARCH));
    }

    @Test
    void skipsWhenAlreadyNominatedThisMonthEvenIfEligible() {
        long now = System.currentTimeMillis();
        Month monthOfNow = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).getMonth();

        assertEquals(NominationOutcome.SKIPPED_ALREADY_THIS_MONTH,
            NominationService.decideOutcome(3, now, monthOfNow));
    }

    @Test
    void nominatesWhenLastNominationWasADifferentMonth() {
        long timestamp = System.currentTimeMillis();
        Month timestampMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
        Month differentMonth = timestampMonth.plus(1);

        assertEquals(NominationOutcome.NOMINATED,
            NominationService.decideOutcome(2, timestamp, differentMonth));
    }

    @Test
    void skipsBelowThresholdWhenDifferentMonthButUnderTwoRequirements() {
        long timestamp = System.currentTimeMillis();
        Month timestampMonth = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).getMonth();
        Month differentMonth = timestampMonth.plus(1);

        assertEquals(NominationOutcome.SKIPPED_BELOW_THRESHOLD,
            NominationService.decideOutcome(1, timestamp, differentMonth));
    }
}
