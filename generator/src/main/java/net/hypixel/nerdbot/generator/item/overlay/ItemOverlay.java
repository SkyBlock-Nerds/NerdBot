package net.hypixel.nerdbot.generator.item.overlay;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

@Getter
public class ItemOverlay {
    private String name;
    private String[] overlays;
    private boolean isBig;
    private String colorOptions;
    private int x;
    private int y;
    private OverlayType type;
    @Setter
    private OverlayColorOptions overlayColorOptions;
    @Setter
    private transient BufferedImage image;
}
