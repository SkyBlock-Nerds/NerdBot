package net.hypixel.nerdbot.app.feature;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.time.Duration;

/**
 * Hourly Drive permission reconcile: heals role changes missed while offline, members who left
 * unseen, failed grant/revoke calls, and permissions deleted by hand in Drive. The roles listener is
 * the primary path; this is the safety net. Does nothing when Drive isn't configured.
 */
@Slf4j
public class DrivePermissionSyncFeature extends BotFeature {

    private static final Duration PERIOD = Duration.ofHours(1);

    private final DriveSyncSweep sweep;
    private final Provider<BotState> state;

    @Inject
    public DrivePermissionSyncFeature(DriveSyncSweep sweep, Provider<BotState> state) {
        this.sweep = sweep;
        this.state = state;
    }

    @Override
    public void onFeatureStart() {
        if (state.get().isReadOnly()) {
            log.warn("Not syncing Drive permissions; the bot is in read-only mode");
            return;
        }
        scheduleAtFixedRate("drive-permission-sync-task", sweep::run, PERIOD.toMillis(), PERIOD.toMillis());
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
