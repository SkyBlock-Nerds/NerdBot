package net.hypixel.nerdbot.app.nomination;

/** The nomination and inactivity sweeps moderators can force, behind an interface so the commands can be tested. */
public interface NominationSweeps {

    NominationSweepReport nominateMembers();

    NominationSweepReport nominateNewMembers();

    InactivitySweepReport checkMemberInactivity();

    InactivitySweepReport checkNewMemberInactivity();

    void checkRestrictedChannelInactivity();
}
