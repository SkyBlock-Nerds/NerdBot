package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.util.Item;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.spritesheet.ItemSpritesheet;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftItemGenerator implements Generator {

    private final String itemId;
    private final boolean enchanted;
    private BufferedImage itemImage;

    public MinecraftItemGenerator(String itemId) {
        this.itemId = itemId;
        this.enchanted = false;
    }

    @Override
    public Item generate() {
        itemImage = ItemSpritesheet.getTexture(itemId);

        if (itemImage.getWidth() <= 16 && itemImage.getHeight() <= 16) {
            itemImage = ImageUtil.upscaleImage(itemImage, 20);
        }

        if (enchanted) {
            itemImage = applyEnchantGlint();
        }

        return new Item(itemImage);
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private boolean enchanted;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public MinecraftItemGenerator.Builder isEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        @Override
        public MinecraftItemGenerator build() {
            return new MinecraftItemGenerator(itemId, enchanted);
        }
    }

    private BufferedImage applyEnchantGlint() {
        try {
            BufferedImage glintImage = ImageIO.read(new File("src/main/resources/minecraft/textures/overlays.png"));
            glintImage = ImageUtil.resizeImage(glintImage, itemImage.getWidth(), itemImage.getHeight());

            BufferedImage enchantedItem = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = enchantedItem.createGraphics();

            g.drawImage(itemImage, 0, 0, null);

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.33F));
            g.drawImage(glintImage, 0, 0, null);

            g.dispose();

            return enchantedItem;
        } catch (IOException e) {
            throw new GeneratorException("An error occurred while applying the enchant glint to the item!");
        }
    }
}
