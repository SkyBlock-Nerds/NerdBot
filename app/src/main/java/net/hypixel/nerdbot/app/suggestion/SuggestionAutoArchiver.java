package net.hypixel.nerdbot.app.suggestion;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.hypixel.nerdbot.app.config.NerdBotConfig;

import java.util.concurrent.TimeUnit;

/** Archives and locks suggestion threads that have gone quiet, as part of the hourly refresh. */
@Slf4j
public final class SuggestionAutoArchiver {

    record Decision(boolean archive, boolean lock) {

        boolean any() {
            return archive || lock;
        }
    }

    private SuggestionAutoArchiver() {
    }

    /** Queues the archive and lock the thread is due, if any. */
    public static void apply(ThreadChannel thread, Suggestion.ChannelType channelType, NerdBotConfig config, long nowMillis) {
        if (channelType == Suggestion.ChannelType.UNKNOWN) {
            return;
        }

        boolean normal = channelType == Suggestion.ChannelType.NORMAL;
        int archiveAfterHours = normal ? config.getSuggestionConfig().getAutoArchiveThreshold() : config.getAlphaProjectConfig().getAutoArchiveThreshold();
        int lockAfterHours = normal ? config.getSuggestionConfig().getAutoLockThreshold() : config.getAlphaProjectConfig().getAutoLockThreshold();
        long lastActivity = thread.getLatestMessageIdLong() != 0
            ? TimeUtil.getTimeCreated(thread.getLatestMessageIdLong()).toInstant().toEpochMilli()
            : thread.getTimeCreated().toInstant().toEpochMilli();
        long hoursInactive = TimeUnit.MILLISECONDS.toHours(nowMillis - lastActivity);

        Decision decision = decide(hoursInactive, archiveAfterHours, lockAfterHours, thread.isArchived(), thread.isLocked());
        if (!decision.any()) {
            return;
        }

        ThreadChannelManager manager = thread.getManager();
        if (decision.archive()) {
            manager = manager.setArchived(true);
        }
        if (decision.lock()) {
            manager = manager.setLocked(true);
        }

        log.info("Suggestion '{}' (ID: {}) has had no activity for {} hours (archive: {}, lock: {})",
            thread.getName(), thread.getId(), hoursInactive, decision.archive(), decision.lock());
        manager.queue(null, failure -> log.warn("Failed to auto-archive/lock suggestion '{}' (ID: {})", thread.getName(), thread.getId(), failure));
    }

    /**
     * A threshold of zero or less turns that action off. Threads that are already archived are left
     * alone: Discord won't edit an archived thread (including locking it) without unarchiving it first.
     */
    static Decision decide(long hoursInactive, int archiveAfterHours, int lockAfterHours, boolean archived, boolean locked) {
        if (archived) {
            return new Decision(false, false);
        }

        boolean archive = archiveAfterHours > 0 && hoursInactive >= archiveAfterHours;
        boolean lock = !locked && lockAfterHours > 0 && hoursInactive >= lockAfterHours;
        return new Decision(archive, lock);
    }
}
