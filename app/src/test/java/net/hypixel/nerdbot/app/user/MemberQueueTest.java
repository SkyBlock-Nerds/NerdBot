package net.hypixel.nerdbot.app.user;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberQueueTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final MemberQueue queue = new MemberQueue(users, pool);

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    @Test
    void oneMembersWorkRunsInTheOrderItWasQueued() {
        List<Integer> ran = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> queued = IntStream.range(0, 200)
            .mapToObj(step -> queue.run("1", "step", () -> ran.add(step)))
            .toList();
        CompletableFuture.allOf(queued.toArray(CompletableFuture[]::new)).join();

        assertEquals(IntStream.range(0, 200).boxed().toList(), ran);
    }

    @Test
    void differentMembersAreHandledAtTheSameTime() throws InterruptedException {
        CountDownLatch bothStarted = new CountDownLatch(2);
        Runnable waitForTheOther = () -> {
            bothStarted.countDown();
            try {
                assertTrue(bothStarted.await(5, TimeUnit.SECONDS), "the other member's work never started");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };

        CompletableFuture<Void> first = queue.run("1", "wait", waitForTheOther);
        CompletableFuture<Void> second = queue.run("2", "wait", waitForTheOther);

        CompletableFuture.allOf(first, second).join();
        assertEquals(0, bothStarted.getCount());
    }

    @Test
    void aFailureDoesNotStopTheMembersLaterWork() {
        List<String> ran = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> failed = queue.run("1", "broken", () -> {
            throw new IllegalStateException("boom");
        });
        queue.run("1", "after", () -> ran.add("after")).join();

        assertThrows(RuntimeException.class, failed::join);
        assertEquals(List.of("after"), ran);
    }

    @Test
    void withRecordHandsOverTheStoredRecord() {
        DiscordUser stored = new DiscordUser("1");
        users.seed(stored);
        List<DiscordUser> seen = new ArrayList<>();

        queue.withRecord("1", "check", seen::add).join();

        assertEquals(1, seen.size());
        assertSame(stored, seen.getFirst());
    }

    @Test
    void withoutARecordTheChangeIsSkipped() {
        List<DiscordUser> seen = new ArrayList<>();

        queue.withRecord("1", "check", seen::add).join();
        queue.withRecord("1", "check", seen::add).join();

        assertTrue(seen.isEmpty());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void aRecordCreatedByEarlierWorkIsSeenByLaterWork() {
        List<DiscordUser> seen = Collections.synchronizedList(new ArrayList<>());

        queue.run("1", "arrival", () -> users.seed(new DiscordUser("1")));
        queue.withRecord("1", "check", seen::add).join();

        assertEquals(1, seen.size());
    }

    @Test
    void workRunsOffTheCallingThread() {
        Thread caller = Thread.currentThread();
        List<Thread> ranOn = new ArrayList<>();

        queue.run("1", "where", () -> ranOn.add(Thread.currentThread())).join();

        assertFalse(ranOn.contains(caller));
    }
}
