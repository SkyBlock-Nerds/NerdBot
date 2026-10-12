package net.hypixel.nerdbot.app.inject;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;

import java.io.IOException;
import java.nio.file.Path;
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

    Path configFile();

    /** Set with {@code -Dbot.readOnly=true}, for running against live data without the scheduled work writing to it. */
    boolean isReadOnly();

    /** @throws IllegalStateException when the file can't be read or parsed; the live config is left alone */
    void loadConfig();

    /** Writes the config file atomically and makes {@code config} the live config. */
    void saveConfig(DiscordBotConfig config) throws IOException;
}
