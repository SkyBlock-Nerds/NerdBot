package net.hypixel.nerdbot.app.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
public class GeneratorConfig {

    /**
     * Player head generator configuration
     */
    private PlayerHeadConfig playerHead = new PlayerHeadConfig();

    /**
     * Tooltip generator configuration
     */
    private TooltipConfig tooltip = new TooltipConfig();

    /**
     * Item generator configuration
     */
    private ItemConfig item = new ItemConfig();

    /**
     * Inventory generator configuration
     */
    private InventoryConfig inventory = new InventoryConfig();

    /**
     * Cache configuration
     */
    private CacheConfig cache = new CacheConfig();


    /**
     * General generator configuration
     */
    private GeneralConfig general = new GeneralConfig();

    /**
     * Resource pack configuration
     */
    private ResourcePackConfig resourcePacks = new ResourcePackConfig();

    @Getter
    @Setter
    @ToString
    public static class PlayerHeadConfig {
        /**
         * Default skin texture ID for player heads
         */
        private String defaultSkinTexture = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb";

        /**
         * Default scale factor for player heads
         */
        private int defaultScale = 1;

        /**
         * Maximum allowed scale factor
         */
        private int maxScale = 20;

        /**
         * Minimum allowed scale factor
         */
        private int minScale = -10;
    }

    @Getter
    @Setter
    @ToString
    public static class TooltipConfig {
        /**
         * Default maximum line length for tooltips
         */
        private int defaultMaxLineLength = 36;

        /**
         * Default alpha value for tooltip background
         */
        private int defaultAlpha = 230;

        /**
         * Default padding for tooltips
         */
        private int defaultPadding = 0;

        /**
         * Whether to render borders by default
         */
        private boolean defaultRenderBorder = true;

        /**
         * Whether to center text by default
         */
        private boolean defaultCenteredText = false;

        /**
         * Maximum allowed line length
         */
        private int maxLineLength = 100;

        /**
         * Minimum allowed line length
         */
        private int minLineLength = 10;
    }

    @Getter
    @Setter
    @ToString
    public static class ItemConfig {
        /**
         * Whether items are enchanted by default
         */
        private boolean defaultEnchanted = false;

        /**
         * Whether hover effect is enabled by default
         */
        private boolean defaultHoverEffect = false;

        /**
         * Default durability percentage (null = no durability bar)
         */
        private Integer defaultDurabilityPercent = null;
    }

    @Getter
    @Setter
    @ToString
    public static class InventoryConfig {
        /**
         * Whether to draw borders by default
         */
        private boolean defaultDrawBorder = true;

        /**
         * Whether to draw background by default
         */
        private boolean defaultDrawBackground = true;

        /**
         * Maximum number of rows allowed
         */
        private int maxRows = 100;

        /**
         * Maximum number of columns allowed
         */
        private int maxColumns = 100;

        /**
         * Minimum number of rows allowed
         */
        private int minRows = 1;

        /**
         * Minimum number of columns allowed
         */
        private int minColumns = 1;

        /**
         * Whether enchanted items in inventories should animate their glint
         */
        private boolean animateGlint = false;
    }

    @Getter
    @Setter
    @ToString
    public static class CacheConfig {
        /**
         * Whether caching is enabled
         */
        private boolean enabled = true;

        /**
         * Maximum number of entries in the cache
         */
        private int maxSize = 1000;

        /**
         * Time-to-live for cache entries in minutes
         */
        private int ttlMinutes = 60;

    }


    @Getter
    @Setter
    @ToString
    public static class ResourcePackConfig {
        /**
         * Resource packs to register with the image generator at startup
         */
        private List<PackDefinition> packs = new ArrayList<>();

        /**
         * The pack ID applied when a generator command does not specify one (null or empty = vanilla)
         */
        private String defaultPack = null;
    }

    @Getter
    @Setter
    @ToString
    public static class PackDefinition {
        /**
         * Unique pack identifier in namespace:name format (e.g. hypixel:skyblock)
         */
        private String id;

        /**
         * Path to the pack zip file or extracted pack directory
         */
        private String path;

        /**
         * Tooltip style refs keyed by rarity name, e.g. "legendary" -> "hypixel_skyblock:legendary".
         * Rarities without an entry render with the pack's default tooltip override, if any.
         */
        private Map<String, String> tooltipStyles = new LinkedHashMap<>();

        /**
         * Text color replacements applied to tooltip text, keyed by vanilla hex color,
         * e.g. "#AA0000" -> "#D13228", mirroring the pack's text shader palette swap.
         */
        private Map<String, String> textColorRemap = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class GeneralConfig {
        /**
         * Default scale factor for generators
         */
        private int defaultScaleFactor = 1;

        /**
         * Default padding for generators
         */
        private int defaultPadding = 0;

        /**
         * Maximum allowed scale factor
         */
        private int maxScaleFactor = 10;

        /**
         * Minimum allowed scale factor
         */
        private int minScaleFactor = 1;

        /**
         * Whether debug logging is enabled for generators
         */
        private boolean debugLogging = false;

        /**
         * Whether error details should be included in responses
         */
        private boolean includeErrorDetails = true;
    }
}
