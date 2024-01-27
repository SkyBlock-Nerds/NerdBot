package net.hypixel.nerdbot.generator;

import lombok.Getter;

import java.awt.image.BufferedImage;

@Getter
public class GeneratedItem {

    private final BufferedImage image;

    public GeneratedItem(BufferedImage image) {
        this.image = image;
    }
}
