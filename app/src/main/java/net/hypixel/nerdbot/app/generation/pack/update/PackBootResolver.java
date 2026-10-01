package net.hypixel.nerdbot.app.generation.pack.update;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.config.GeneratorConfig;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * At startup, points each auto-updated pack at its cached zip when the state file says one was
 * applied and the file is still intact. Anything else falls back to the configured path, with
 * the reason logged.
 */
@Slf4j
public final class PackBootResolver {

    private PackBootResolver() {
    }

    /** Lowercase pack id to cached zip, for packs whose cached copy should be loaded instead of the configured path. */
    public static Map<String, Path> cachedPaths(GeneratorConfig.ResourcePackConfig config, PackCacheStore store) {
        Map<String, Path> overrides = new HashMap<>();

        for (GeneratorConfig.PackDefinition definition : PackUpdater.enabledDefinitions(config)) {
            String packId = PackUpdater.packIdOf(definition);
            PackState.AppliedPack current = store.slot(packId).current();

            if (current == null) {
                continue;
            }

            Optional<Path> zip = store.verifiedZip(current);
            if (zip.isPresent()) {
                log.info("Loading pack '{}' from the cached format {} pack {}", packId, current.packFormat(), current.sha1());
                overrides.put(packId, zip.get());
            } else {
                log.warn("Cached pack {} for '{}' is missing or changed on disk, loading the configured path instead", current.fileName(), packId);
                // The configured pack is live now, so the next check must re-apply instead of reporting up to date
                store.recordFallback(packId);
            }
        }

        return overrides;
    }
}
