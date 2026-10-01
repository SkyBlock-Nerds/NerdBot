package net.hypixel.nerdbot.app.generation.pack.update;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.generation.pack.PackConfigMapper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                log.info("Loading pack '{}' from the cached format {} pack {} instead of the configured {} (delete pack-state.json or disable autoUpdate to go back)",
                    packId, current.packFormat(), current.sha1(), definition.getPath());
                overrides.put(packId, zip.get());
            } else {
                log.warn("Cached pack {} for '{}' is missing or changed on disk, loading the configured path instead", current.fileName(), packId);
                // The configured pack is live now, so the next check must re-apply instead of reporting up to date
                store.recordFallback(packId);
            }
        }

        return overrides;
    }

    /**
     * Handles cached packs that the registration did not load: the library skips a pack that fails
     * to register, which would leave it missing even though the configured pack could serve. Each
     * such pack's state is reset so the next check re-applies it, and the configured packs are
     * registered once more (the library skips ids that are already registered).
     */
    public static void repairUnregistered(GeneratorConfig.ResourcePackConfig config, Map<String, Path> overrides,
                                          ResourcePackService service, PackCacheStore store) {
        Set<PackId> registered = service.packRepository().registeredPacks();
        boolean repaired = false;

        for (Map.Entry<String, Path> override : overrides.entrySet()) {
            if (registered.contains(PackId.parse(override.getKey()))) {
                continue;
            }

            log.warn("Cached pack file {} for '{}' failed to register, loading the configured path instead", override.getValue(), override.getKey());
            store.recordFallback(override.getKey());
            repaired = true;
        }

        if (repaired) {
            service.registerConfiguredPacks(PackConfigMapper.toRegistrationConfig(config));
        }
    }
}
