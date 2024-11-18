package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.JsonLoader;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Log4j2
@Getter
@Setter
@AllArgsConstructor
@ToString
public class PowerStrength {

    private static final List<PowerStrength> POWER_STRENGTHS = new ArrayList<>();

    static {
        try {
            List<PowerStrength> powerStrengths = JsonLoader.loadFromJson(PowerStrength[].class, Objects.requireNonNull(PowerStrength.class.getClassLoader().getResource("data/powerStrengths.json")));
            POWER_STRENGTHS.addAll(powerStrengths);
        } catch (Exception e) {
            log.error("Failed to load power strength data", e);
        }
    }

    private final String name;
    private final String display;
    private final boolean stone;

    public static PowerStrength byName(String name) {
        return POWER_STRENGTHS.stream()
            .filter(powerStrength -> powerStrength.getDisplay().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public static List<String> getPowerStrengthNames() {
        return POWER_STRENGTHS.stream().map(PowerStrength::getName).collect(Collectors.toList());
    }

    public String getFormattedDisplay() {
        return display + (stone ? " Stone" : "") + " Power";
    }
}