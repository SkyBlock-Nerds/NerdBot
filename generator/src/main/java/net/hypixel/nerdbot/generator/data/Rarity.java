package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.json.JsonLoader;
import net.hypixel.nerdbot.generator.text.ChatFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Rarity {

    private static final List<Rarity> RARITIES = new ArrayList<>();

    static {
        try {
            RARITIES.addAll(JsonLoader.loadFromJson(Rarity[].class, Objects.requireNonNull(Rarity.class.getClassLoader().getResource("data/rarities.json"))));
        } catch (Exception e) {
            log.error("Failed to load rarity data", e);
        }
    }

    private final String name;
    private final String display;
    private final ChatFormat color;

    public static Rarity byName(String name) {
        return RARITIES.stream()
            .filter(rarity -> rarity.getDisplay().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public static List<String> getRarityNames() {
        return RARITIES.stream().map(Rarity::getName).collect(Collectors.toList());
    }

    public static List<Rarity> getAllRarities() {
        return Collections.unmodifiableList(RARITIES);
    }

    public String getColorCode() {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + color.getCode();
    }

    public String getFormattedDisplay() {
        return getColorCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + display;
    }
}
