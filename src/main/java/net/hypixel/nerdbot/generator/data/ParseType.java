package net.hypixel.nerdbot.generator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.JsonLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Log4j2
@Getter
@Setter
@AllArgsConstructor
@ToString
public class ParseType {

    private static final List<ParseType> PARSE_TYPES = new ArrayList<>();

    static {
        try {
            PARSE_TYPES.addAll(JsonLoader.loadFromJson(ParseType[].class, Objects.requireNonNull(ParseType.class.getClassLoader().getResource("data/parse_types.json"))));
            log.info("Loaded " + PARSE_TYPES.size() + " parse types!");
        } catch (Exception e) {
            log.error("Failed to load parse type data", e);
        }
    }

    private String name;
    private String formatWithDetails;
    private String formatWithoutDetails;

    public static ParseType byName(String name) {
        return PARSE_TYPES.stream()
            .filter(parseType -> parseType.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}