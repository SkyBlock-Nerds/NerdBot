package net.hypixel.nerdbot.generator.item.overlay;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Set;

@ToString
public class OverlayColorOptions {

    @Getter
    private final String name;
    private final HashMap<String, int[]> options;
    @Getter
    private final HashMap<Integer, Integer> map;
    @Getter
    private final boolean allowHexColors;
    private final boolean useDefaultIfMissing;
    @Getter
    private final int[] defaultColors;

    private transient Cache<@NotNull String, int[]> parsedHexCache;

    public OverlayColorOptions(String name, HashMap<String, int[]> options, HashMap<Integer, Integer> map,
                               boolean allowHexColors, boolean useDefaultIfMissing, int[] defaultColors) {
        this.name = name;
        this.options = options;
        this.map = map;
        this.allowHexColors = allowHexColors;
        this.useDefaultIfMissing = useDefaultIfMissing;
        this.defaultColors = defaultColors;
    }

    public int[] getColorsFromOption(String option) {
        option = option != null ? option.toLowerCase() : "";

        // Check predefined options first
        int[] foundColors = options.get(option);
        if (foundColors != null) {
            return foundColors;
        }

        if (allowHexColors) {
            if (parsedHexCache == null) {
                parsedHexCache = Caffeine.newBuilder()
                    .maximumSize(100)
                    .build();
            }

            final String finalOption = option;
            int[] cachedColors = parsedHexCache.get(option, key -> {
                try {
                    String[] colors = finalOption.split(",");
                    int[] colorValues = new int[colors.length];
                    for (int i = 0; i < colors.length; i++) {
                        colorValues[i] = Color.decode(colors[i].replaceAll("[^#a-fA-F0-9]", "")).getRGB();
                    }
                    return colorValues;
                } catch (NumberFormatException e) {
                    return null;
                }
            });

            if (cachedColors != null) {
                return cachedColors;
            }
        }

        return useDefaultIfMissing ? defaultColors : null;
    }

    /**
     * Returns all available option names for this color options set.
     *
     * @return Set of option names
     */
    public Set<String> getOptionNames() {
        return options != null ? options.keySet() : Set.of();
    }
}
