package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.ImageUtil;

import java.awt.image.BufferedImage;

@Getter
@Setter
public class GeneratedItem {

    protected BufferedImage image;

    public GeneratedItem(BufferedImage image) {
        this.image = image;
    }
}
