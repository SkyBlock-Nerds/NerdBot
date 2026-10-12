package net.hypixel.nerdbot.app.curator;

import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One run of the curator over a forum. The fields are written by the curating thread and can be
 * read from another, which is how {@code /curate} shows progress.
 */
public class Curation {

    private final boolean dryRun;
    private final List<GreenlitMessage> greenlit = new CopyOnWriteArrayList<>();
    private volatile long startedAt;
    private volatile long finishedAt;
    private volatile int index;
    private volatile int total;
    private volatile ThreadChannel current;

    /**
     * @param dryRun counts votes without greenlighting or saving anything
     */
    public Curation(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /** The suggestions greenlit by this run. */
    public List<GreenlitMessage> greenlit() {
        return List.copyOf(greenlit);
    }

    public long startedAt() {
        return startedAt;
    }

    public long durationMs() {
        return finishedAt - startedAt;
    }

    public int index() {
        return index;
    }

    public int total() {
        return total;
    }

    /** The thread being curated, or null before the first and after the last. */
    public ThreadChannel current() {
        return current;
    }

    public void start(long now, int total) {
        this.startedAt = now;
        this.total = total;
    }

    public void moveTo(int index, ThreadChannel thread) {
        this.index = index;
        this.current = thread;
    }

    public void addGreenlit(GreenlitMessage message) {
        greenlit.add(message);
    }

    public void finish(long now) {
        this.finishedAt = now;
        this.current = null;
    }
}
