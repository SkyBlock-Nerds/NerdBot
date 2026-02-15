package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.nomination.NominationInactivityService;
import net.hypixel.nerdbot.app.nomination.NominationService;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;

import java.time.Duration;
import java.util.TimerTask;

@Slf4j
public class UserNominationFeature extends BotFeature {

    public static void nominateUsers() {
        NominationService.getInstance().runMemberNominationSweep();
    }

    public static void nominateNewMembers() {
        NominationService.getInstance().runNewMemberNominationSweep();
    }

    public static void findInactiveUsers() {
        NominationInactivityService.getInstance().runInactivitySweepForMembers();
    }

    public static void findInactiveNewMembers() {
        NominationInactivityService.getInstance().runInactivitySweepForNewMembers();
    }

    public static void findInactiveUsersInRoleRestrictedChannels() {
        NominationInactivityService.getInstance().runRoleRestrictedInactivitySweep();
    }

    @Override
    public void onFeatureStart() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (TimeUtils.isDayOfMonth(1) && SkyBlockNerdsBot.config().isNominationsEnabled()) {
                    log.info("Running nomination check");
                    nominateUsers();
                }

                if (TimeUtils.isDayOfMonth(15) && SkyBlockNerdsBot.config().isInactivityCheckEnabled()) {
                    log.info("Running inactivity check");
                    findInactiveUsers();
                    findInactiveUsersInRoleRestrictedChannels();
                }
            }
        }, 0, Duration.ofHours(1).toMillis());
    }

    @Override
    public void onFeatureEnd() {

    }
}