package net.hypixel.nerdbot.app.command.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressUpdatesTest {

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
