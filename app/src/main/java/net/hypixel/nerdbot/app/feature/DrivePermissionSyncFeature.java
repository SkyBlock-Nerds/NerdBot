package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.drive.DriveSyncSweep;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.util.concurrent.TimeUnit;

/**
 * Hourly Drive permission reconcile: heals role changes missed while offline,
 * members who left unseen, failed grant/revoke calls, and permissions deleted
 * by hand in Drive. Event-driven updates (DrivePermissionListener) are the
 * primary path; this is the safety net. No-ops when the Drive service is
 * unconfigured.
 */
@Slf4j
public class DrivePermissionSyncFeature extends BotFeature {

    private static final long PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    public void onFeatureStart() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.warn("Bot is in read-only mode, skipping Drive permission sync task!");
            return;
        }
        scheduleAtFixedRate("drive-permission-sync-task", this::sweep, PERIOD_MS, PERIOD_MS);
    }

    private void sweep() {
        if (SkyBlockNerdsBot.drivePermissionService().isEmpty()) {
            return;
        }
        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            log.error("Skipping Drive permission sweep as the database is not connected!");
            return;
        }
        new DriveSyncSweep().run();
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
