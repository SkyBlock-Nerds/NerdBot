package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.app.nomination.NominationInactivityService.RoleRestrictedOutcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static net.hypixel.nerdbot.app.nomination.NominationInactivityService.classifyRoleRestrictedMember;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleRestrictedInactivityDecisionTest {

    private static final YearMonth OCTOBER_2026 = YearMonth.of(2026, 10);

    @Test
    void membersMeetingAllRequirementsAreNotWarned() {
        assertEquals(RoleRestrictedOutcome.ACTIVE, classifyRoleRestrictedMember(3, null, OCTOBER_2026));
    }

    @Test
    void membersMeetingTwoRequirementsAreNotWarned() {
        assertEquals(RoleRestrictedOutcome.ACTIVE, classifyRoleRestrictedMember(2, null, OCTOBER_2026));
    }

    @Test
    void membersMeetingFewerThanTwoRequirementsAreWarned() {
        assertEquals(RoleRestrictedOutcome.WARN, classifyRoleRestrictedMember(1, null, OCTOBER_2026));
        assertEquals(RoleRestrictedOutcome.WARN, classifyRoleRestrictedMember(0, null, OCTOBER_2026));
    }

    @Test
    void secondRunInTheSameMonthDoesNotWarnAgain() {
        assertEquals(RoleRestrictedOutcome.ALREADY_WARNED_THIS_MONTH,
            classifyRoleRestrictedMember(0, epochMillisAt(2026, 10, 15), OCTOBER_2026));
    }

    @Test
    void warningFromAPreviousMonthDoesNotBlockANewOne() {
        assertEquals(RoleRestrictedOutcome.WARN, classifyRoleRestrictedMember(0, epochMillisAt(2026, 9, 15), OCTOBER_2026));
    }

    @Test
    void sameMonthLastYearDoesNotBlockAWarning() {
        assertEquals(RoleRestrictedOutcome.WARN, classifyRoleRestrictedMember(0, epochMillisAt(2025, 10, 15), OCTOBER_2026));
    }

    @Test
    void activeMembersAreReportedAsActiveEvenIfWarnedThisMonth() {
        assertEquals(RoleRestrictedOutcome.ACTIVE, classifyRoleRestrictedMember(3, epochMillisAt(2026, 10, 15), OCTOBER_2026));
    }

    private static long epochMillisAt(int year, int month, int day) {
        return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
