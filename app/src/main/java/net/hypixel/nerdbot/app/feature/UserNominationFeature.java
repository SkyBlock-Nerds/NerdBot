package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.nomination.NominationInactivityService;
import net.hypixel.nerdbot.app.nomination.NominationService;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class UserNominationFeature extends BotFeature {

    static final int NOMINATION_DAY = 1;
    static final int INACTIVITY_CHECK_DAY = 15;

    enum Sweep {
        MEMBER_NOMINATIONS,
        NEW_MEMBER_NOMINATIONS,
        MEMBER_INACTIVITY,
        NEW_MEMBER_INACTIVITY,
        ROLE_RESTRICTED_INACTIVITY
    }

    /**
     * The feature runs hourly, so each sweep runs on every hour of its day. That's safe because every
     * sweep only nominates or warns a member once per calendar month.
     */
    static List<Sweep> sweepsDueOn(int dayOfMonth, boolean nominationsEnabled, boolean inactivityChecksEnabled) {
        List<Sweep> sweeps = new ArrayList<>();

        if (dayOfMonth == NOMINATION_DAY && nominationsEnabled) {
            sweeps.add(Sweep.MEMBER_NOMINATIONS);
            sweeps.add(Sweep.NEW_MEMBER_NOMINATIONS);
        }

        if (dayOfMonth == INACTIVITY_CHECK_DAY && inactivityChecksEnabled) {
            sweeps.add(Sweep.MEMBER_INACTIVITY);
            sweeps.add(Sweep.NEW_MEMBER_INACTIVITY);
            sweeps.add(Sweep.ROLE_RESTRICTED_INACTIVITY);
        }

        return sweeps;
    }

    @Override
    public void onFeatureStart() {
        scheduleAtFixedRate("UserNominationFeature-task", () -> {
            List<Sweep> due = sweepsDueOn(
                LocalDate.now().getDayOfMonth(),
                SkyBlockNerdsBot.config().isNominationsEnabled(),
                SkyBlockNerdsBot.config().isInactivityCheckEnabled()
            );

            for (Sweep sweep : due) {
                log.info("Running {} sweep", sweep);

                try {
                    run(sweep);
                } catch (RuntimeException exception) {
                    log.error("{} sweep failed", sweep, exception);
                }
            }
        }, 0, Duration.ofHours(1).toMillis());
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
