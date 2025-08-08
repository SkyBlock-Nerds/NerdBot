package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.util.JsonLoader;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Gemstone {

    private static final List<Gemstone> GEMSTONES = new ArrayList<>();

    static {
        try {
            GEMSTONES.addAll(JsonLoader.loadFromJson(Gemstone[].class, Objects.requireNonNull(Gemstone.class.getClassLoader().getResource("data/gemstones.json"))));
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
