package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.util.JsonLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Icon {

    private static final List<Icon> ICONS = new ArrayList<>();

    static {
        try {
            ICONS.addAll(JsonLoader.loadFromJson(Icon[].class, Objects.requireNonNull(Icon.class.getClassLoader().getResource("data/icons.json"))));
        } catch (Exception e) {
            log.error("Failed to load icon data", e);
        }
    }

    private String name;
    private String icon;

    public static Icon byName(String name) {
        return ICONS.stream()
            .filter(icon -> icon.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
