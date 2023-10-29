package net.hypixel.nerdbot.generator.util;

import java.awt.image.BufferedImage;

public class Item {

    private final BufferedImage image;

    public Item(BufferedImage image) {
        this.image = image;
    }

    public BufferedImage getImage() {
        return image;
    }
}
