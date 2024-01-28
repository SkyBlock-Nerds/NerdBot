package net.hypixel.nerdbot.generator;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

@Getter
@Setter
public class GeneratedItem {

    private BufferedImage image;

    public GeneratedItem(BufferedImage image) {
        this.image = image;
    }
}
