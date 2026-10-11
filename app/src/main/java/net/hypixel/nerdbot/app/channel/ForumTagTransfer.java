package net.hypixel.nerdbot.app.channel;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/** Swaps one forum tag for another on every thread that has it, open or archived. */
@Slf4j
public class ForumTagTransfer {

    public record Outcome(int moved, int failed) {
    }

    @Inject
    public ForumTagTransfer() {
    }

    /** Pages through the archived threads too, so this blocks: call it off the dispatch thread. */
    public List<ThreadChannel> threadsWithTag(ForumChannel forum, ForumTag tag) {
        return Stream.concat(forum.getThreadChannels().stream(), forum.retrieveArchivedPublicThreadChannels().stream())
            .filter(thread -> thread.getAppliedTags().contains(tag))
            .distinct()
            .toList();
    }

    /**
     * Moves the threads one at a time, so Discord's rate limit is never hit in a burst.
     *
     * @param onProgress called with the number of threads handled so far
     */
    public CompletableFuture<Outcome> transfer(List<ThreadChannel> threads, ForumTag from, ForumTag to, IntConsumer onProgress) {
        CompletableFuture<Outcome> chain = CompletableFuture.completedFuture(new Outcome(0, 0));

        for (ThreadChannel thread : threads) {
            chain = chain.thenCompose(sofar -> move(thread, from, to).thenApply(moved -> {
                Outcome next = moved ? new Outcome(sofar.moved() + 1, sofar.failed()) : new Outcome(sofar.moved(), sofar.failed() + 1);
                onProgress.accept(next.moved() + next.failed());
                return next;
            }));
        }

        return chain;
    }

    /**
     * An archived thread is reopened for the edit and archived again afterwards, even when the
     * edit fails, so a failure never leaves it open.
     *
     * @return whether the tags changed
     */
    CompletableFuture<Boolean> move(ThreadChannel thread, ForumTag from, ForumTag to) {
        boolean wasArchived = thread.isArchived();
        List<ForumTag> tags = swapped(thread.getAppliedTags(), from, to);

        CompletableFuture<Void> reopened = wasArchived ? thread.getManager().setArchived(false).submit() : CompletableFuture.completedFuture(null);
        CompletableFuture<Throwable> edited = reopened
            .thenCompose(open -> thread.getManager().setAppliedTags(tags).submit())
            .handle((done, error) -> error);

        return edited.thenCompose(error -> {
            if (error != null) {
                log.warn("Failed to move thread {} from tag {} to {}", thread.getId(), from.getName(), to.getName(), error);
            }

            CompletableFuture<Void> closed = wasArchived ? thread.getManager().setArchived(true).submit() : CompletableFuture.completedFuture(null);
            return closed.handle((done, closeError) -> {
                if (closeError != null) {
                    log.warn("Failed to archive thread {} again after moving its tag", thread.getId(), closeError);
                }
                return error == null;
            });
        });
    }

    static List<ForumTag> swapped(List<ForumTag> applied, ForumTag from, ForumTag to) {
        List<ForumTag> tags = new ArrayList<>(applied);
        tags.remove(from);
        if (!tags.contains(to)) {
            tags.add(to);
        }
        return tags;
    }
}
