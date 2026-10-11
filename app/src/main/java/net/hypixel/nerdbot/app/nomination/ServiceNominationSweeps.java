package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;

public class ServiceNominationSweeps implements NominationSweeps {

    @Inject
    public ServiceNominationSweeps() {
    }

    @Override
    public NominationSweepReport nominateMembers() {
        return NominationService.getInstance().runMemberNominationSweep();
    }

    @Override
    public NominationSweepReport nominateNewMembers() {
        return NominationService.getInstance().runNewMemberNominationSweep();
    }

    @Override
    public InactivitySweepReport checkMemberInactivity() {
        return NominationInactivityService.getInstance().runInactivitySweepForMembers();
    }

    @Override
    public InactivitySweepReport checkNewMemberInactivity() {
        return NominationInactivityService.getInstance().runInactivitySweepForNewMembers();
    }

    @Override
    public void checkRestrictedChannelInactivity() {
        NominationInactivityService.getInstance().runRoleRestrictedInactivitySweep();
    }
}
