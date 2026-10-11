package net.hypixel.nerdbot.app.inject;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;

import java.util.Optional;

/**
 * Parts of the bot that are set up during startup or replaced while it runs. Code reads them
 * through a {@code Provider} on each use: {@code /config reload} swaps the config, and the pack
 * updater and Drive service only exist when configured.
 */
public interface BotState {

    NerdBotConfig currentConfig();

    Optional<PackUpdater> currentPackUpdater();

    Optional<DrivePermissionService> currentDrivePermissionService();
}
