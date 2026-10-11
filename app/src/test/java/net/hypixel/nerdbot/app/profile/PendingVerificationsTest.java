package net.hypixel.nerdbot.app.profile;

import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingVerificationsTest {

    private static final String MEMBER = "100";

    private final MutableClock clock = new MutableClock();
    private final PendingVerifications pending = new PendingVerifications(clock);
    private final MojangProfile profile = new MojangProfile();

    @Test
    void nothingIsPendingAtFirst() {
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
        assertEquals(Optional.empty(), pending.accept(MEMBER));
    }

    @Test
    void acceptingReturnsTheProfileOnce() {
        pending.submit(MEMBER, profile);

        assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
        assertEquals(Optional.of(profile), pending.accept(MEMBER));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
        assertEquals(Optional.empty(), pending.accept(MEMBER));
    }

    @Test
    void aRequestExpiresADayAfterItWasMadeHoweverOftenItIsChecked() {
        pending.submit(MEMBER, profile);

        for (int hour = 0; hour < 23; hour++) {
            clock.advance(Duration.ofHours(1));
            assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
        }
        clock.advance(Duration.ofHours(1));

        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
        assertEquals(Optional.empty(), pending.accept(MEMBER));
    }

    @Test
    void aDeclineBlocksForADay() {
        pending.submit(MEMBER, profile);
        clock.advance(Duration.ofHours(12));
        pending.decline(MEMBER);

        clock.advance(Duration.ofHours(23));
        assertEquals(PendingVerifications.State.DECLINED, pending.state(MEMBER));

        clock.advance(Duration.ofHours(1));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void acceptingADeclinedRequestLeavesTheDecline() {
        pending.decline(MEMBER);

        assertEquals(Optional.empty(), pending.accept(MEMBER));
        assertEquals(PendingVerifications.State.DECLINED, pending.state(MEMBER));
    }

    @Test
    void clearingForgetsTheRequest() {
        pending.submit(MEMBER, profile);

        pending.clear(MEMBER);

        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void membersAreTrackedSeparately() {
        pending.submit(MEMBER, profile);
        pending.decline("200");

        assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
        assertEquals(PendingVerifications.State.DECLINED, pending.state("200"));
    }
}
