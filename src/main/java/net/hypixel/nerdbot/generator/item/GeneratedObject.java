package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

@Getter
@Setter
public class GeneratedObject {

    private BufferedImage image;

    public GeneratedObject(BufferedImage image) {
        this.image = image;
    }
}
