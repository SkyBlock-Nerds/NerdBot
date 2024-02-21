package net.hypixel.nerdbot.generator.item.overlay;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.*;
import java.util.HashMap;

@AllArgsConstructor
public class OverlayColorOptions {

    @Getter
    private final String name;
    private final HashMap<String, int[]> options;
    private boolean allowHexColors;
    private boolean useDefaultIfMissing;
    private int[] defaultColors;
    @Getter
    private final HashMap<Integer, Integer> map;

    public int[] getColorFromOption(String option) {
        option = option != null ? option.toLowerCase() : "";

        int[] foundColors = options.get(option);
        if (foundColors != null) {
            return foundColors;
        }

        if (allowHexColors) {
            try {
                return new int[] { Color.decode(option.replaceAll("[^#a-fA-F0-9]", "")).getRGB() };
            } catch (NumberFormatException ignored) {}
        }

        return useDefaultIfMissing ? defaultColors : null;
    }
}
