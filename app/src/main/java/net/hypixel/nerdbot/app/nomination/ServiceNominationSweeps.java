package net.hypixel.nerdbot.app.nomination;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Keeps a forced sweep from running alongside the scheduled ones, which could otherwise nominate or
 * warn a member twice before either recorded it.
 */
@Slf4j
@Singleton
public class ServiceNominationSweeps implements NominationSweeps {

    private final PromotionNominations nominations;
    private final InactivityWarnings warnings;
    private final ReentrantLock running = new ReentrantLock();

    @Inject
    public ServiceNominationSweeps(PromotionNominations nominations, InactivityWarnings warnings) {
        this.nominations = nominations;
        this.warnings = warnings;
    }

    @Override
    public Optional<SweepReport> nominateMembers() {
        return exclusively("member nominations", nominations::nominateMembers);
    }

    @Override
    public Optional<SweepReport> nominateNewMembers() {
        return exclusively("new member nominations", nominations::nominateNewMembers);
    }

    @Override
    public Optional<SweepReport> checkMemberInactivity() {
        return exclusively("member inactivity", warnings::checkMembers);
    }

    @Override
    public Optional<SweepReport> checkNewMemberInactivity() {
        return exclusively("new member inactivity", warnings::checkNewMembers);
    }

    @Override
    public Optional<List<SweepReport>> checkRestrictedChannelInactivity() {
        return exclusively("restricted channel inactivity", warnings::checkRestrictedGroups);
    }

    private <T> Optional<T> exclusively(String sweep, Supplier<T> run) {
        if (!running.tryLock()) {
            log.warn("Not starting the {} sweep; another sweep is still running", sweep);
            return Optional.empty();
        }

        try {
            return Optional.of(run.get());
        } finally {
            running.unlock();
        }
    }
}
