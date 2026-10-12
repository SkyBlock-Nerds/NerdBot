package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static net.hypixel.nerdbot.app.nomination.SweepFixture.MEMBER_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNominationSweepsTest {

    private final SweepFixture fixture = new SweepFixture();
    private final CountDownLatch sweepStarted = new CountDownLatch(1);
    private final CountDownLatch finishSweep = new CountDownLatch(1);
    private final ServiceNominationSweeps sweeps = new ServiceNominationSweeps(fixture.nominations(), fixture.warnings());

    @Test
    void aSweepRunsWhenNothingElseIs() {
        fixture.member("1", MEMBER_ROLE);

        Optional<SweepReport> report = sweeps.checkMemberInactivity();

        assertEquals(1, report.orElseThrow().acted());
    }

    @Test
    void aSecondSweepDoesNotStartWhileOneIsRunning() throws Exception {
        fixture.member("1", MEMBER_ROLE);
        SweepFixture.RecordingVotingChannel channel = fixture.votingChannel;
        ServiceNominationSweeps blocking = new ServiceNominationSweeps(fixture.nominations(), new InactivityWarnings(fixture.sweep, new VotingChannel() {
            @Override
            public void requireAvailable() {
                sweepStarted.countDown();
                await(finishSweep);
            }

            @Override
            public CompletableFuture<Void> send(MessageEmbed embed) {
                return channel.send(embed);
            }
        }, fixture.openTickets, fixture.users, () -> fixture.config, fixture.clock));

        CompletableFuture<Optional<SweepReport>> first = CompletableFuture.supplyAsync(blocking::checkMemberInactivity);
        assertTrue(sweepStarted.await(5, TimeUnit.SECONDS));

        assertEquals(Optional.empty(), blocking.nominateMembers());
        assertEquals(Optional.empty(), blocking.checkRestrictedChannelInactivity());

        finishSweep.countDown();
        assertEquals(1, first.get(5, TimeUnit.SECONDS).orElseThrow().acted());
        assertTrue(blocking.nominateMembers().isPresent());
    }

    @Test
    void aFailedSweepFreesTheNextOne() {
        fixture.votingChannel.available = false;
        assertThrows(IllegalStateException.class, sweeps::nominateMembers);
        fixture.votingChannel.available = true;

        assertEquals(Optional.of(List.of()), sweeps.checkRestrictedChannelInactivity());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
