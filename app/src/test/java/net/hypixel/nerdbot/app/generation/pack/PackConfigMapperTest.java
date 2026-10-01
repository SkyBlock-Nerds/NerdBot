package net.hypixel.nerdbot.app.generation.pack;

import net.aerh.imagegenerator.tools.pack.PackDefinition;
import net.aerh.imagegenerator.tools.pack.PackRegistrationConfig;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackConfigMapperTest {

    @Test
    void nullConfigMapsToNull() {
        assertNull(PackConfigMapper.toRegistrationConfig(null));
    }

    @Test
    void mapsEveryField() {
        GeneratorConfig.PackDefinition definition = new GeneratorConfig.PackDefinition();
        definition.setId("hypixel:skyblock");
        definition.setPath("/app/packs/hypixel-skyblock.zip");
        definition.setTooltipStyles(Map.of("legendary", "hypixel_skyblock:legendary"));
        definition.setTextColorRemap(Map.of("#AA0000", "#D13228"));
        GeneratorConfig.ResourcePackConfig config = new GeneratorConfig.ResourcePackConfig();
        config.setPacks(List.of(definition));
        config.setDefaultPack("hypixel:skyblock");

        PackRegistrationConfig mapped = PackConfigMapper.toRegistrationConfig(config);

        assertEquals("hypixel:skyblock", mapped.defaultPack());
        assertEquals(List.of(new PackDefinition("hypixel:skyblock", "/app/packs/hypixel-skyblock.zip",
            Map.of("legendary", "hypixel_skyblock:legendary"), Map.of("#AA0000", "#D13228"))), mapped.packs());
    }

    @Test
    void nullPackListAndNullEntriesAreDropped() {
        GeneratorConfig.ResourcePackConfig noList = new GeneratorConfig.ResourcePackConfig();
        noList.setPacks(null);
        assertTrue(PackConfigMapper.toRegistrationConfig(noList).packs().isEmpty());

        GeneratorConfig.ResourcePackConfig withNull = new GeneratorConfig.ResourcePackConfig();
        List<GeneratorConfig.PackDefinition> packs = new ArrayList<>();
        packs.add(null);
        withNull.setPacks(packs);
        assertTrue(PackConfigMapper.toRegistrationConfig(withNull).packs().isEmpty());
    }

    @Test
    void nullMapsOnADefinitionBecomeEmpty() {
        GeneratorConfig.PackDefinition definition = new GeneratorConfig.PackDefinition();
        definition.setId("a:b");
        definition.setPath("p");
        definition.setTooltipStyles(null);
        definition.setTextColorRemap(null);
        GeneratorConfig.ResourcePackConfig config = new GeneratorConfig.ResourcePackConfig();
        config.setPacks(List.of(definition));

        PackDefinition mapped = PackConfigMapper.toRegistrationConfig(config).packs().get(0);

        assertTrue(mapped.tooltipStyles().isEmpty());
        assertTrue(mapped.textColorRemap().isEmpty());
    }
}
