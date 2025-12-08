package net.hypixel.nerdbot.generator.item.overlay;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for overlay rendering operations.
 * Contains color data, color mappings, and additional parameters.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OverlayConfig {

    private final int[] colors;
    private final int[] defaultColors;
    private final Map<Integer, Integer> colorMap;
    private final String dataOption;
    private final Map<String, Object> additionalParams;

    /**
     * Builder for constructing OverlayConfig instances.
     */
    public static class Builder {
        private final Map<String, Object> additionalParams = new HashMap<>();
        private int[] colors;
        private int[] defaultColors;
        private Map<Integer, Integer> colorMap;
        private String dataOption;

        /**
         * Set the color array for this overlay.
         * For NORMAL overlays, only the first color is used.
         * For DUAL_LAYER overlays, the first two colors are used.
         * For MAPPED overlays, all colors are used as a palette.
         *
         * @param colors Color values as RGB integers
         *
         * @return This builder
         */
        public Builder withColors(int... colors) {
            this.colors = colors;
            return this;
        }

        /**
         * Set the default color array (used for de-tinting).
         *
         * @param defaultColors Default color values as RGB integers
         *
         * @return This builder
         */
        public Builder withDefaultColors(int... defaultColors) {
            this.defaultColors = defaultColors;
            return this;
        }

        /**
         * Set the color mapping for MAPPED overlays.
         * Maps source pixel colors to palette indices.
         *
         * @param colorMap Mapping from source RGB values to palette indices
         *
         * @return This builder
         */
        public Builder withColorMap(Map<Integer, Integer> colorMap) {
            this.colorMap = colorMap;
            return this;
        }

        /**
         * Set the data option string (e.g., "red", "blue", "#FF0000").
         *
         * @param dataOption The data option string
         *
         * @return This builder
         */
        public Builder withDataOption(String dataOption) {
            this.dataOption = dataOption;
            return this;
        }

        /**
         * Add a parameter.
         *
         * @param key   Parameter key
         * @param value Parameter value
         *
         * @return This builder
         */
        public Builder withParam(String key, Object value) {
            this.additionalParams.put(key, value);
            return this;
        }

        /**
         * Build the OverlayConfig instance.
         *
         * @return New OverlayConfig
         */
        public OverlayConfig build() {
            return new OverlayConfig(
                colors,
                defaultColors,
                colorMap != null ? Map.copyOf(colorMap) : null,
                dataOption,
                Map.copyOf(additionalParams)
            );
        }
    }
}