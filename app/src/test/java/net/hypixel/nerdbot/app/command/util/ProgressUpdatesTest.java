package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.interactions.InteractionHook;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressUpdatesTest {

    @Test
    void throttledReportsEditAtMostOncePerInterval() {
        MutableClock clock = new MutableClock();
        List<String> edits = new ArrayList<>();
        ProgressUpdates progress = ProgressUpdates.throttled(Duration.ofSeconds(10), clock, edits::add);

        progress.report("1/100");
        clock.advance(Duration.ofSeconds(4));
        progress.report("40/100");
        clock.advance(Duration.ofSeconds(6));
        progress.report("70/100");

        assertEquals(List.of("1/100", "70/100"), edits);
    }

    @Test
    void nothingIsReportedAfterTheFinalUpdate() {
        MutableClock clock = new MutableClock();
        List<String> edits = new ArrayList<>();
        ProgressUpdates progress = ProgressUpdates.throttled(Duration.ofSeconds(10), clock, edits::add);

        progress.finish(() -> edits.add("done"));
        clock.advance(Duration.ofMinutes(1));
        progress.report("late status");

        assertEquals(List.of("done"), edits);
    }

    @Test
    void aFailingEditDoesNotStopLaterOnes() {
        MutableClock clock = new MutableClock();
        List<String> edits = new ArrayList<>();
        ProgressUpdates progress = ProgressUpdates.throttled(Duration.ofSeconds(1), clock, status -> {
            if (status.equals("bad")) {
                throw new IllegalStateException("hook expired");
            }
            edits.add(status);
        });

        progress.report("bad");
        clock.advance(Duration.ofSeconds(1));
        progress.report("good");

        assertEquals(List.of("good"), edits);
    }

    @Test
    void throttledReplyEditsTheHookUntilItExpires() {
        CallRecorder recorder = new CallRecorder();
        boolean[] expired = {false};
        InteractionHook hook = recorder.fake(InteractionHook.class, Map.of("isExpired", (CallRecorder.Answer) args -> expired[0]));
        MutableClock clock = new MutableClock();
        ProgressUpdates progress = ProgressUpdates.throttledReply(hook, Duration.ofSeconds(1), clock);

        progress.loggingReporter().accept("halfway");
        assertEquals("halfway", recorder.last("editOriginal").arg(0));

        expired[0] = true;
        clock.advance(Duration.ofSeconds(2));
        progress.report("too late");
        assertEquals(1, recorder.calls("editOriginal").size());
    }

    @Test
    void reportIsOnlyForThrottledProgress() {
        ProgressUpdates progress = new ProgressUpdates(() -> {
        });

        assertThrows(IllegalStateException.class, () -> progress.report("status"));
    }

    @Test
    void ticksRunTheUpdateUntilFinished() {
        List<String> edits = new ArrayList<>();
        ProgressUpdates progress = new ProgressUpdates(() -> edits.add("progress"));

        progress.tick();
        progress.tick();
        progress.finish(() -> edits.add("final"));
        progress.tick();

        assertEquals(List.of("progress", "progress", "final"), edits);
    }

    @Test
    void finalUpdateOnlyRunsOnce() {
        AtomicInteger finals = new AtomicInteger();
        ProgressUpdates progress = new ProgressUpdates(() -> {
        });

        progress.finish(finals::incrementAndGet);
        progress.finish(finals::incrementAndGet);

        assertEquals(1, finals.get());
    }

    @Test
    void aFailingUpdateDoesNotStopLaterOnes() {
        AtomicInteger attempts = new AtomicInteger();
        ProgressUpdates progress = new ProgressUpdates(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("edit failed");
        });

        progress.tick();
        progress.tick();

        assertEquals(2, attempts.get());
    }

    @Test
    void finishWaitsForAnInFlightUpdateSoTheFinalEditComesLast() throws InterruptedException {
        List<String> edits = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch releaseUpdate = new CountDownLatch(1);
        ProgressUpdates progress = new ProgressUpdates(() -> {
            updateStarted.countDown();
            await(releaseUpdate);
            edits.add("progress");
        });

        Thread ticker = new Thread(progress::tick);
        ticker.start();
        assertTrue(updateStarted.await(5, TimeUnit.SECONDS));

        Thread finisher = new Thread(() -> progress.finish(() -> edits.add("final")));
        finisher.start();
        waitUntilBlockedOrDone(finisher);
        releaseUpdate.countDown();
        ticker.join(5_000);
        finisher.join(5_000);

        assertEquals(List.of("progress", "final"), edits);
    }

    @Test
    void scheduledUpdatesStopOnceFinished() throws InterruptedException {
        AtomicInteger updates = new AtomicInteger();
        CountDownLatch firstUpdate = new CountDownLatch(1);
        ProgressUpdates progress = ProgressUpdates.every(Duration.ofMillis(5), "progress-test", () -> {
            updates.incrementAndGet();
            firstUpdate.countDown();
        });

        assertTrue(firstUpdate.await(5, TimeUnit.SECONDS));
        progress.finish(() -> {
        });
        int afterFinish = updates.get();
        Thread.sleep(50);

        assertEquals(afterFinish, updates.get());
    }

    private static void waitUntilBlockedOrDone(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.getState() != Thread.State.TERMINATED) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Finisher never reached the lock");
            }
            Thread.sleep(1);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
