package net.hypixel.nerdbot.app.nomination;

import java.util.List;
import java.util.Optional;

/**
 * The nomination and inactivity sweeps, run monthly and forced by moderators. Only one runs at a
 * time; each returns empty when another one is already running.
 */
public interface NominationSweeps {

    Optional<SweepReport> nominateMembers();

    Optional<SweepReport> nominateNewMembers();

    Optional<SweepReport> checkMemberInactivity();

    Optional<SweepReport> checkNewMemberInactivity();

    Optional<List<SweepReport>> checkRestrictedChannelInactivity();
}
