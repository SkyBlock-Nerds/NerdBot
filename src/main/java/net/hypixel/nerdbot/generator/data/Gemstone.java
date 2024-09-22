package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.JsonLoader;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log4j2
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Gemstone {

    private static List<Gemstone> GEMSTONES;

    static {
        try {
            GEMSTONES = new ArrayList<>(JsonLoader.loadFromJson(Gemstone[].class, Objects.requireNonNull(Gemstone.class.getResource("/data/gemstones.json"))));
        } catch (Exception e) {
            log.error("Failed to load gemstone data", e);
        }
    }

    private String name;
    private String icon;
    private String formattedIcon;
    private Color color;
    private Map<String, String> formattedTiers;

    public static Gemstone byName(String name) {
        return GEMSTONES.stream()
            .filter(gemstone -> gemstone.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
