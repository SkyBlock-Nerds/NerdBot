package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;

@Getter
@Setter
public class GeneratedObject {

    protected BufferedImage image;

    public GeneratedObject(BufferedImage image) {
        this.image = image;
    }
}
