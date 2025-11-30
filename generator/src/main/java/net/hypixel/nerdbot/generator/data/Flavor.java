package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.JsonLoader;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Flavor {

    private static final List<Flavor> FLAVORS = new ArrayList<>();

    static {
        try {
            FLAVORS.addAll(JsonLoader.loadFromJson(Flavor[].class, Objects.requireNonNull(Flavor.class.getClassLoader().getResource("data/flavor.json"))));
        } catch (Exception e) {
            log.error("Failed to load flavor text data", e);
        }
    }

    private String icon;
    private String name;
    private String stat;
    private String display;
    private ChatFormat color;
    @Nullable
    private ChatFormat subColor;
    private String parseType;

    public static Flavor byName(String name) {
        return FLAVORS.stream()
            .filter(flavor -> flavor.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public static List<Flavor> getFlavors() {
        return List.copyOf(FLAVORS);
    }

    public ChatFormat getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}