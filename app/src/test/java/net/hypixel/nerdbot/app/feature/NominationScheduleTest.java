package net.hypixel.nerdbot.app.feature;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.feature.UserNominationFeature.Sweep;
import net.hypixel.nerdbot.app.nomination.SweepLog;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominationScheduleTest {

    private static final List<Sweep> NOMINATIONS = List.of(Sweep.MEMBER_NOMINATIONS, Sweep.NEW_MEMBER_NOMINATIONS);
    private static final List<Sweep> INACTIVITY = List.of(Sweep.MEMBER_INACTIVITY, Sweep.NEW_MEMBER_INACTIVITY, Sweep.ROLE_RESTRICTED_INACTIVITY);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T09:00:00Z"));
    private final NerdBotConfig config = new NerdBotConfig();
    private final MapSweepLog sweepLog = new MapSweepLog();
    private final List<Sweep> ran = new ArrayList<>();
    private Set<Sweep> failing = Set.of();

    private final UserNominationFeature feature = new UserNominationFeature(sweepLog, clock, () -> config, sweep -> {
        ran.add(sweep);
        if (failing.contains(sweep)) {
            throw new IllegalStateException("guild not loaded");
        }
    });

    @Test
    void nominationsRunOnTheFirstAndAreLogged() {
        sweepLog.runs.put("nominations", YearMonth.of(2026, 9));

        feature.runDueSweeps();

        assertEquals(NOMINATIONS, ran);
        assertEquals(OCTOBER, sweepLog.runs.get("nominations"));
    }

    @Test
    void aBatchRunsOnceAMonth() {
        sweepLog.runs.put("nominations", YearMonth.of(2026, 9));

        feature.runDueSweeps();
        clock.advance(Duration.ofHours(1));
        feature.runDueSweeps();

        assertEquals(NOMINATIONS, ran);
    }

    @Test
    void aBatchMissedWhileTheBotWasDownRunsWhenItsBack() {
        sweepLog.runs.put("nominations", YearMonth.of(2026, 9));
        sweepLog.runs.put("inactivity-checks", YearMonth.of(2026, 9));
        clock.advance(Duration.ofDays(17));

        feature.runDueSweeps();

        assertEquals(concat(NOMINATIONS, INACTIVITY), ran);
        assertEquals(OCTOBER, sweepLog.runs.get("inactivity-checks"));
    }

    @Test
    void inactivityChecksWaitForTheFifteenth() {
        sweepLog.runs.put("nominations", OCTOBER);
        sweepLog.runs.put("inactivity-checks", YearMonth.of(2026, 9));
        clock.advance(Duration.ofDays(13));

        feature.runDueSweeps();
        assertTrue(ran.isEmpty());

        clock.advance(Duration.ofDays(1));
        feature.runDueSweeps();
        assertEquals(INACTIVITY, ran);
    }

    @Test
    void aFailedSweepLeavesTheBatchToBeTriedAgain() {
        sweepLog.runs.put("nominations", YearMonth.of(2026, 9));
        failing = Set.of(Sweep.NEW_MEMBER_NOMINATIONS);

        feature.runDueSweeps();
        assertEquals(YearMonth.of(2026, 9), sweepLog.runs.get("nominations"));

        failing = Set.of();
        clock.advance(Duration.ofHours(1));
        feature.runDueSweeps();

        assertEquals(concat(NOMINATIONS, NOMINATIONS), ran);
        assertEquals(OCTOBER, sweepLog.runs.get("nominations"));
    }

    @Test
    void withNoLogYetARunOnTheDayItself() {
        feature.runDueSweeps();

        assertEquals(NOMINATIONS, ran);
    }

    @Test
    void withNoLogYetAfterTheDayThisMonthCountsAsDone() {
        clock.advance(Duration.ofDays(19));

        feature.runDueSweeps();

        assertTrue(ran.isEmpty());
        assertEquals(OCTOBER, sweepLog.runs.get("nominations"));
        assertEquals(OCTOBER, sweepLog.runs.get("inactivity-checks"));
    }

    @Test
    void disabledBatchesNeitherRunNorGetLogged() {
        config.setNominationsEnabled(false);
        config.setInactivityCheckEnabled(false);
        sweepLog.runs.put("nominations", YearMonth.of(2026, 9));
        sweepLog.runs.put("inactivity-checks", YearMonth.of(2026, 9));
        clock.advance(Duration.ofDays(16));

        feature.runDueSweeps();

        assertTrue(ran.isEmpty());
        assertEquals(YearMonth.of(2026, 9), sweepLog.runs.get("nominations"));
    }

    private static List<Sweep> concat(List<Sweep> first, List<Sweep> second) {
        List<Sweep> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static final class MapSweepLog implements SweepLog {
        private final Map<String, YearMonth> runs = new HashMap<>();

        @Override
        public Optional<YearMonth> lastRun(String sweep) {
            return Optional.ofNullable(runs.get(sweep));
        }

        @Override
        public void recordRun(String sweep, YearMonth month) {
            runs.put(sweep, month);
        }
    }
}
