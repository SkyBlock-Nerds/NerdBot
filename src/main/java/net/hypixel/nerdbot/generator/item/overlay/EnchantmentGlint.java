package net.hypixel.nerdbot.generator.item.overlay;

import net.hypixel.nerdbot.generator.spritesheet.OverlaySheet;

import java.awt.*;
import java.awt.image.BufferedImage;

public class EnchantmentGlint {

    public static BufferedImage applyEnchantGlint(BufferedImage image) {
        BufferedImage enchantedItem = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D enchantedItemGraphics = enchantedItem.createGraphics();
        enchantedItemGraphics.drawImage(image, 0, 0, null);

        BufferedImage glintImage = OverlaySheet.getEnchantGlint(image.getWidth() == 16);
        enchantedItemGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.33F));
        enchantedItemGraphics.drawImage(glintImage, 0, 0, null);

        enchantedItemGraphics.dispose();

        return enchantedItem;
    }
}
