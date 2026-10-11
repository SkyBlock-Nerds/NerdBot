package net.hypixel.nerdbot.discord.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualScheduledExecutorTest {

    private final ManualScheduledExecutor executor = new ManualScheduledExecutor();
    private final List<String> ran = new ArrayList<>();

    @Test
    void tasksRunOnlyWhenTimeReachesThemInDueOrder() {
        executor.schedule(() -> ran.add("later"), 2, TimeUnit.HOURS);
        executor.schedule(() -> ran.add("sooner"), 1, TimeUnit.HOURS);

        executor.advance(Duration.ofMinutes(59));
        assertTrue(ran.isEmpty());

        executor.advance(Duration.ofHours(2));
        assertEquals(List.of("sooner", "later"), ran);
        assertTrue(executor.pendingDelays().isEmpty());
    }

    @Test
    void pendingDelaysCountFromNow() {
        executor.schedule(() -> ran.add("task"), 90, TimeUnit.MINUTES);

        executor.advance(Duration.ofMinutes(30));

        assertEquals(List.of(Duration.ofHours(1)), executor.pendingDelays());
    }

    @Test
    void cancelledTasksNeverRun() {
        ScheduledFuture<?> future = executor.schedule(() -> ran.add("task"), 1, TimeUnit.MINUTES);

        assertTrue(future.cancel(false));
        executor.advance(Duration.ofHours(1));

        assertTrue(ran.isEmpty());
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void aTaskThatRanCannotBeCancelled() {
        ScheduledFuture<?> future = executor.schedule(() -> ran.add("task"), 1, TimeUnit.MINUTES);
        executor.advance(Duration.ofMinutes(1));

        assertFalse(future.cancel(false));
        assertTrue(future.isDone());
    }

    @Test
    void tasksScheduledByARunningTaskRunInTheSameAdvanceWhenDue() {
        executor.schedule(() -> {
            ran.add("first");
            executor.schedule(() -> ran.add("second"), 1, TimeUnit.MINUTES);
        }, 1, TimeUnit.MINUTES);

        executor.advance(Duration.ofMinutes(5));

        assertEquals(List.of("first", "second"), ran);
    }

    @Test
    void negativeDelaysRunOnTheNextAdvance() {
        executor.schedule(() -> ran.add("task"), -5, TimeUnit.SECONDS);

        executor.advance(Duration.ZERO);

        assertEquals(List.of("task"), ran);
    }

    @Test
    void repeatingTasksAreNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> executor.scheduleAtFixedRate(() -> { }, 1, 1, TimeUnit.SECONDS));
    }

    @Test
    void nothingIsAcceptedAfterShutdown() {
        executor.schedule(() -> ran.add("task"), 1, TimeUnit.MINUTES);

        assertEquals(1, executor.shutdownNow().size());
        assertThrows(IllegalStateException.class, () -> executor.schedule(() -> { }, 1, TimeUnit.MINUTES));
    }
}
