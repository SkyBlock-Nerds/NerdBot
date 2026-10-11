package net.hypixel.nerdbot.app.feature;

import net.hypixel.nerdbot.app.feature.UserNominationFeature.Sweep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.hypixel.nerdbot.app.feature.UserNominationFeature.sweepsDueOn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominationScheduleTest {

    @Test
    void firstOfTheMonthRunsBothNominationSweeps() {
        assertEquals(List.of(Sweep.MEMBER_NOMINATIONS, Sweep.NEW_MEMBER_NOMINATIONS), sweepsDueOn(1, true, true));
    }

    @Test
    void fifteenthRunsAllThreeInactivitySweeps() {
        assertEquals(
            List.of(Sweep.MEMBER_INACTIVITY, Sweep.NEW_MEMBER_INACTIVITY, Sweep.ROLE_RESTRICTED_INACTIVITY),
            sweepsDueOn(15, true, true)
        );
    }

    @Test
    void otherDaysRunNothing() {
        for (int day = 2; day <= 31; day++) {
            if (day != 15) {
                assertTrue(sweepsDueOn(day, true, true).isEmpty(), "day " + day);
            }
        }
    }

    @Test
    void disabledNominationsSkipBothNominationSweeps() {
        assertTrue(sweepsDueOn(1, false, true).isEmpty());
    }

    @Test
    void disabledInactivityChecksSkipAllInactivitySweeps() {
        assertTrue(sweepsDueOn(15, true, false).isEmpty());
    }
}
