package net.hypixel.nerdbot.app.channel;

import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumTagTransferTest {

    private static final ForumTag OLD = tag("1", "Old");
    private static final ForumTag NEW = tag("2", "New");
    private static final ForumTag OTHER = tag("3", "Other");

    private final ForumTagTransfer transfer = new ForumTagTransfer();
    private final List<String> requests = new ArrayList<>();

    @Test
    void theOldTagIsSwappedForTheNewInPlace() {
        assertEquals(List.of(OTHER, NEW), ForumTagTransfer.swapped(List.of(OLD, OTHER), OLD, NEW));
    }

    @Test
    void aThreadAlreadyCarryingTheNewTagGetsItOnce() {
        assertEquals(List.of(NEW), ForumTagTransfer.swapped(List.of(OLD, NEW), OLD, NEW));
    }

    @Test
    void anOpenThreadOnlyHasItsTagsChanged() {
        assertTrue(transfer.move(thread("10", false, List.of(OLD), false), OLD, NEW).join());

        assertEquals(List.of("10 tags [New]"), requests);
    }

    @Test
    void anArchivedThreadIsReopenedThenArchivedAgain() {
        assertTrue(transfer.move(thread("10", true, List.of(OLD, OTHER), false), OLD, NEW).join());

        assertEquals(List.of("10 archived false", "10 tags [Other, New]", "10 archived true"), requests);
    }

    @Test
    void aFailedEditStillArchivesTheThreadAgain() {
        assertFalse(transfer.move(thread("10", true, List.of(OLD), true), OLD, NEW).join());

        assertEquals(List.of("10 archived false", "10 tags [New]", "10 archived true"), requests);
    }

    @Test
    void theTransferCountsMovesAndFailuresAndReportsProgress() {
        List<Integer> progress = new ArrayList<>();
        List<ThreadChannel> threads = List.of(thread("10", false, List.of(OLD), false), thread("11", false, List.of(OLD), true),
            thread("12", true, List.of(OLD), false));

        ForumTagTransfer.Outcome outcome = transfer.transfer(threads, OLD, NEW, progress::add).join();

        assertEquals(new ForumTagTransfer.Outcome(2, 1), outcome);
        assertEquals(List.of(1, 2, 3), progress);
    }

    @Test
    void threadsAreMovedOneAtATime() {
        List<ThreadChannel> threads = List.of(thread("10", true, List.of(OLD), false), thread("11", true, List.of(OLD), false));

        transfer.transfer(threads, OLD, NEW, done -> {
        }).join();

        assertEquals(List.of("10 archived false", "10 tags [New]", "10 archived true", "11 archived false", "11 tags [New]", "11 archived true"), requests);
    }

    @Test
    void bothOpenAndArchivedThreadsWithTheTagAreFound() {
        ThreadChannel open = thread("10", false, List.of(OLD), false);
        ThreadChannel archived = thread("11", true, List.of(OLD, OTHER), false);
        ThreadChannel untagged = thread("12", true, List.of(OTHER), false);
        ThreadChannelPaginationAction archivedPages = Fakes.of(ThreadChannelPaginationAction.class,
            Map.of("stream", (CallRecorder.Answer) args -> Stream.of(archived, untagged, open)));
        ForumChannel forum = Fakes.of(ForumChannel.class, Map.of("getThreadChannels", List.of(open), "retrieveArchivedPublicThreadChannels", archivedPages));

        assertEquals(List.of(open, archived), transfer.threadsWithTag(forum, OLD));
    }

    private ThreadChannel thread(String id, boolean archived, List<ForumTag> tags, boolean tagEditFails) {
        String[] pending = new String[1];
        ThreadChannelManager[] manager = new ThreadChannelManager[1];
        manager[0] = Fakes.of(ThreadChannelManager.class, Map.of(
            "setArchived", (CallRecorder.Answer) args -> {
                pending[0] = id + " archived " + args[0];
                return manager[0];
            },
            "setAppliedTags", (CallRecorder.Answer) args -> {
                pending[0] = id + " tags " + names(args[0]);
                return manager[0];
            },
            "submit", (CallRecorder.Answer) args -> {
                requests.add(pending[0]);
                return tagEditFails && pending[0].contains("tags")
                    ? CompletableFuture.failedFuture(new IllegalStateException("missing permission"))
                    : CompletableFuture.completedFuture(null);
            }));

        return Fakes.of(ThreadChannel.class, Map.of("getId", id, "isArchived", archived, "getAppliedTags", tags, "getManager", manager[0]));
    }

    private static List<String> names(Object tags) {
        return ((Collection<?>) tags).stream().map(tag -> ((ForumTag) tag).getName()).toList();
    }

    private static ForumTag tag(String id, String name) {
        return Fakes.of(ForumTag.class, Map.of("getId", id, "getName", name));
    }
}
