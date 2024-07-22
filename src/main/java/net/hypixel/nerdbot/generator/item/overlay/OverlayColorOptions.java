package net.hypixel.nerdbot.generator.item.overlay;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.awt.Color;
import java.util.HashMap;

@AllArgsConstructor
@ToString
public class OverlayColorOptions {

    @Getter
    private final String name;
    private final HashMap<String, int[]> options;
    @Getter
    private final HashMap<Integer, Integer> map;
    private boolean allowHexColors;
    private boolean useDefaultIfMissing;
    private int[] defaultColors;

    public int[] getColorsFromOption(String option) {
        option = option != null ? option.toLowerCase() : "";

        int[] foundColors = options.get(option);
        if (foundColors != null) {
            return foundColors;
        }

        if (allowHexColors) {
            try {
                String[] colors = option.split(",");
                int[] colorValues = new int[colors.length];
                for (int i = 0; i < colors.length; i++) {
                    colorValues[i] = Color.decode(colors[i].replaceAll("[^#a-fA-F0-9]", "")).getRGB();
                }
                return colorValues;
            } catch (NumberFormatException ignored) {
            }
        }

        return useDefaultIfMissing ? defaultColors : null;
    }
}
