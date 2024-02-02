package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
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
    private final boolean bigImage;

    private BufferedImage itemImage;

    @Override
    public GeneratedObject generate() {
        itemImage = ItemSpritesheet.getTexture(itemId.toLowerCase());

        if (itemImage == null) {
            throw new GeneratorException("Item with ID " + itemId + " not found");
        }

        if (bigImage && itemImage.getHeight() <= 16 && itemImage.getWidth() <= 16) {
            itemImage = ImageUtil.upscaleImage(itemImage, 20);
        }

        if (enchanted) {
            itemImage = applyEnchantGlint();
        }

        // TODO overlays
        return new GeneratedObject(itemImage);
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private boolean enchanted;
        private boolean bigImage;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            this.itemId = itemId
                .replace("minecraft:", "")
                .replace("skull", "player_head");
            return this;
        }

        public MinecraftItemGenerator.Builder isEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        public MinecraftItemGenerator.Builder isBigImage(boolean bigImage) {
            this.bigImage = bigImage;
            return this;
        }

        public MinecraftItemGenerator.Builder isBigImage() {
            return isBigImage(true);
        }

        @Override
        public MinecraftItemGenerator build() {
            return new MinecraftItemGenerator(itemId, enchanted, bigImage);
        }
    }

    private BufferedImage applyEnchantGlint() {
        try {
            BufferedImage glintImage = ImageIO.read(new File("src/main/resources/minecraft/textures/overlays.png"));
            glintImage = glintImage.getSubimage(0, 17, glintImage.getWidth(), glintImage.getHeight() - 17);
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
