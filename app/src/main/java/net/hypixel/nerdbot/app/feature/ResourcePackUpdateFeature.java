package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.generation.pack.update.PackUpdater;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;

import java.util.Optional;

/**
 * Checks the Hypixel pack API for newer versions of every auto-updated pack on a fixed schedule.
 * Enable it by listing this class in the config's features; per-pack autoUpdate settings decide
 * which packs it touches. Skipped in read-only mode.
 */
@Slf4j
public class ResourcePackUpdateFeature extends BotFeature implements SchedulableFeature {

    private static final long DEFAULT_INITIAL_DELAY_MS = 60_000L;
    private static final long DEFAULT_PERIOD_MS = 900_000L;

    @Override
    public void onFeatureStart() {
        // Scheduling is done by the bot from the feature config
    }

    @Override
    public void executeTask() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.debug("Bot is in read-only mode, skipping the resource pack update check");
            return;
        }

        Optional<PackUpdater> updater = SkyBlockNerdsBot.packUpdater();
        if (updater.isEmpty()) {
            log.debug("Resource pack auto-update is not configured, skipping");
            return;
        }

        GeneratorConfig.ResourcePackConfig packConfig = SkyBlockNerdsBot.config().getGeneratorConfig().getResourcePacks();
        for (GeneratorConfig.PackDefinition definition : PackUpdater.enabledDefinitions(packConfig)) {
            updater.get().checkForUpdate(definition);
        }
    }

    @Override
    public long defaultInitialDelayMs(DiscordBotConfig config) {
        return DEFAULT_INITIAL_DELAY_MS;
    }

    @Override
    public long defaultPeriodMs(DiscordBotConfig config) {
        return DEFAULT_PERIOD_MS;
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
