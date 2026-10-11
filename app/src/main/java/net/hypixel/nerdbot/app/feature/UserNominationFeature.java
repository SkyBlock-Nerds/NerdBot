package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.nomination.NominationInactivityService;
import net.hypixel.nerdbot.app.nomination.NominationService;
import net.hypixel.nerdbot.app.nomination.SweepLog;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runs the monthly nomination sweeps from the 1st and the inactivity sweeps from the 15th. Each
 * batch runs once a month, on its day or as soon after it as the bot is up, and is only logged as
 * done once every sweep in it has finished; a failed batch is tried again an hour later.
 */
@Slf4j
public class UserNominationFeature extends BotFeature {

    static final int NOMINATION_DAY = 1;
    static final int INACTIVITY_CHECK_DAY = 15;
    private static final Duration CHECK_PERIOD = Duration.ofHours(1);

    enum Sweep {
        MEMBER_NOMINATIONS,
        NEW_MEMBER_NOMINATIONS,
        MEMBER_INACTIVITY,
        NEW_MEMBER_INACTIVITY,
        ROLE_RESTRICTED_INACTIVITY
    }

    /** Sweeps that run together, from their day of the month on. */
    enum Batch {
        NOMINATIONS("nominations", NOMINATION_DAY, NerdBotConfig::isNominationsEnabled,
            List.of(Sweep.MEMBER_NOMINATIONS, Sweep.NEW_MEMBER_NOMINATIONS)),
        INACTIVITY_CHECKS("inactivity-checks", INACTIVITY_CHECK_DAY, NerdBotConfig::isInactivityCheckEnabled,
            List.of(Sweep.MEMBER_INACTIVITY, Sweep.NEW_MEMBER_INACTIVITY, Sweep.ROLE_RESTRICTED_INACTIVITY));

        final String key;
        final int day;
        final Predicate<NerdBotConfig> enabled;
        final List<Sweep> sweeps;

        Batch(String key, int day, Predicate<NerdBotConfig> enabled, List<Sweep> sweeps) {
            this.key = key;
            this.day = day;
            this.enabled = enabled;
            this.sweeps = sweeps;
        }
    }

    private final SweepLog sweepLog;
    private final Clock clock;
    private final Provider<NerdBotConfig> config;
    private final Consumer<Sweep> runner;

    @Inject
    public UserNominationFeature(SweepLog sweepLog, Clock clock, Provider<NerdBotConfig> config) {
        this(sweepLog, clock, config, UserNominationFeature::run);
    }

    UserNominationFeature(SweepLog sweepLog, Clock clock, Provider<NerdBotConfig> config, Consumer<Sweep> runner) {
        this.sweepLog = sweepLog;
        this.clock = clock;
        this.config = config;
        this.runner = runner;
    }

    @Override
    public void onFeatureStart() {
        scheduleAtFixedRate("UserNominationFeature-task", this::runDueSweeps, 0, CHECK_PERIOD.toMillis());
    }

    void runDueSweeps() {
        LocalDate today = LocalDate.now(clock);
        YearMonth month = YearMonth.from(today);
        NerdBotConfig current = config.get();

        for (Batch batch : Batch.values()) {
            if (!batch.enabled.test(current) || today.getDayOfMonth() < batch.day) {
                continue;
            }

            Optional<YearMonth> lastRun = sweepLog.lastRun(batch.key);
            if (lastRun.isPresent() && !lastRun.get().isBefore(month)) {
                continue;
            }

            // Before runs were logged, every batch ran on its own day, so this month's already happened
            if (lastRun.isEmpty() && today.getDayOfMonth() > batch.day) {
                log.info("No {} run is logged yet; counting {} as done", batch.key, month);
                sweepLog.recordRun(batch.key, month);
                continue;
            }

            if (runAll(batch)) {
                sweepLog.recordRun(batch.key, month);
            }
        }
    }

    private boolean runAll(Batch batch) {
        boolean finished = true;

        for (Sweep sweep : batch.sweeps) {
            log.info("Running {} sweep", sweep);

            try {
                runner.accept(sweep);
            } catch (RuntimeException exception) {
                log.error("{} sweep failed; the {} batch will be tried again", sweep, batch.key, exception);
                finished = false;
            }
        }

        return finished;
    }

    private static void run(Sweep sweep) {
        switch (sweep) {
            case MEMBER_NOMINATIONS -> NominationService.getInstance().runMemberNominationSweep();
            case NEW_MEMBER_NOMINATIONS -> NominationService.getInstance().runNewMemberNominationSweep();
            case MEMBER_INACTIVITY -> NominationInactivityService.getInstance().runInactivitySweepForMembers();
            case NEW_MEMBER_INACTIVITY -> NominationInactivityService.getInstance().runInactivitySweepForNewMembers();
            case ROLE_RESTRICTED_INACTIVITY -> NominationInactivityService.getInstance().runRoleRestrictedInactivitySweep();
        }
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
