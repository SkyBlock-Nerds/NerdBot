package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.JsonLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class ArmorType {

    private static final List<ArmorType> ARMOR_TYPES = new ArrayList<>();

    static {
        try {
            ARMOR_TYPES.addAll(JsonLoader.loadFromJson(ArmorType[].class, Objects.requireNonNull(ArmorType.class.getClassLoader().getResource("data/armor_types.json"))));
        } catch (Exception e) {
            log.error("Failed to load armor type data", e);
        }
    }

    private String materialName;
    private boolean supportsCustomColoring;

    private static final Set<String> COLORABLE_ARMOR_NAMES = ARMOR_TYPES.stream()
        .filter(ArmorType::isSupportsCustomColoring)
        .map(ArmorType::getMaterialName)
        .collect(Collectors.toSet());

    /**
     * Checks if the given overlay name represents colorable armor
     *
     * @param overlayName The overlay name to check
     * @return true if the armor supports custom coloring, false otherwise
     */
    public static boolean isColorableArmor(String overlayName) {
        if (overlayName == null) {
            return false;
        }

        String lowerCaseName = overlayName.toLowerCase();
        boolean isColorable = COLORABLE_ARMOR_NAMES.stream()
            .anyMatch(lowerCaseName::contains);

        log.debug("Checking overlay name '{}' for colorable armor: {}", overlayName, isColorable);
        return isColorable;
    }

    /**
     * Gets the armor type from an overlay name
     *
     * @param overlayName The overlay name to parse
     * @return The matching ArmorType or null if none found
     */
    public static ArmorType fromOverlayName(String overlayName) {
        if (overlayName == null) {
            return null;
        }

        String lowerCaseName = overlayName.toLowerCase();
        return ARMOR_TYPES.stream()
            .filter(type -> lowerCaseName.contains(type.getMaterialName()))
            .findFirst()
            .orElse(null);
    }

}
