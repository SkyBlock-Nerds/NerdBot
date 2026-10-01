package net.hypixel.nerdbot.app.generation.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Turns the bot's JSON-bound pack config into the library's registration records. */
@Slf4j
public final class PackConfigMapper {

    private PackConfigMapper() {
    }

    @Nullable
    public static PackRegistrationConfig toRegistrationConfig(@Nullable GeneratorConfig.ResourcePackConfig config) {
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

            packs.add(new PackDefinition(definition.getId(), definition.getPath(), definition.getTooltipStyles(), definition.getTextColorRemap()));
        }

        return new PackRegistrationConfig(packs, config.getDefaultPack());
    }
}
