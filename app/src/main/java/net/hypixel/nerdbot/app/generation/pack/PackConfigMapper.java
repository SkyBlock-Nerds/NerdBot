package net.hypixel.nerdbot.app.generation.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns the bot's JSON-bound pack config into the library's registration records. */
@Slf4j
public final class PackConfigMapper {

    private PackConfigMapper() {
    }

    @Nullable
    public static PackRegistrationConfig toRegistrationConfig(@Nullable GeneratorConfig.ResourcePackConfig config) {
        return toRegistrationConfig(config, Map.of());
    }

    /**
     * Like {@link #toRegistrationConfig(GeneratorConfig.ResourcePackConfig)}, but packs whose
     * lowercase id is a key of {@code pathOverrides} load from the override path instead of the
     * configured one (used at boot for cached auto-updated packs).
     */
    @Nullable
    public static PackRegistrationConfig toRegistrationConfig(@Nullable GeneratorConfig.ResourcePackConfig config,
                                                              Map<String, Path> pathOverrides) {
        if (config == null) {
            return null;
        }

        List<PackDefinition> packs = new ArrayList<>();
        List<GeneratorConfig.PackDefinition> configured = config.getPacks() == null ? List.of() : config.getPacks();

        for (GeneratorConfig.PackDefinition definition : configured) {
            if (definition == null) {
                log.warn("Skipping null resource pack entry in configuration");
                continue;
            }

            String id = definition.getId() == null ? null : definition.getId().trim().toLowerCase(Locale.ROOT);
            Path override = id == null ? null : pathOverrides.get(id);
            packs.add(toDefinition(definition, override == null ? definition.getPath() : override.toString()));
        }

        return new PackRegistrationConfig(packs, config.getDefaultPack());
    }

    /**
     * Maps one NerdBot pack definition to the library form, loading the pack from {@code path}
     * instead of the configured path.
     */
    public static PackDefinition toDefinition(GeneratorConfig.PackDefinition definition, String path) {
        return new PackDefinition(definition.getId(), path, definition.getTooltipStyles(), definition.getTextColorRemap());
    }
}
