package net.hypixel.nerdbot.generator.item.overlay;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

/**
 * Data class representing an item overlay configuration.
 * Loaded from overlay_coordinates.json.
 */
@Getter
public class ItemOverlay {
    private String name;
    private String colorOptions;
    private String colorMode;
    private int x;
    private int y;
    private int size;
    private String type;
    @Setter
    private OverlayColorOptions overlayColorOptions;
    @Setter
    private transient BufferedImage image;
}
