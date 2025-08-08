package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.overlay.EnchantmentGlint;
import net.hypixel.nerdbot.generator.item.overlay.HoverEffect;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayType;
import net.hypixel.nerdbot.generator.spritesheet.OverlaySheet;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.util.ImageUtil;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftItemGenerator implements Generator {

    private final String itemId;
    private final String data;
    private final boolean enchanted;
    private final boolean hoverEffect;
    private final boolean bigImage;

    private BufferedImage itemImage;

    @Override
    public GeneratedObject generate() {
        itemImage = Spritesheet.getTexture(itemId.toLowerCase());

        if (itemImage == null) {
            throw new GeneratorException("Item with ID `%s` not found", itemId);
        }

        ItemOverlay itemOverlays = OverlaySheet.getOverlay(itemId.toLowerCase());
        if (itemOverlays != null) {
            itemImage = applyOverlay(itemOverlays);
        }

        if (bigImage && itemImage.getHeight() <= 16 && itemImage.getWidth() <= 16) {
            itemImage = ImageUtil.upscaleImage(itemImage, 10);
        }

        if (enchanted) {
            itemImage = EnchantmentGlint.applyEnchantGlint(itemImage);
        }

        if (hoverEffect) {
            itemImage = HoverEffect.applyHoverEffect(itemImage);
        }

        return new GeneratedObject(itemImage);
    }

    private BufferedImage applyOverlay(ItemOverlay overlay) {
        BufferedImage overlayImage = overlay.getImage();
        BufferedImage coloredBaseItem = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        String options = (data != null ? data : "");
        log.debug("Overlay type: {}, Color options: {}, Data: {}", overlay.getType(), overlay.getOverlayColorOptions(), options);
        
        switch (overlay.getType()) {
            case NORMAL -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                log.debug("Retrieved overlay colors: {}", overlayColors != null ? Arrays.toString(overlayColors) : "null");
                if (overlayColors != null) {
                    log.debug("Applying color {} to base item", Integer.toHexString(overlayColors[0]));
                    OverlayType.normalOverlay(coloredBaseItem, itemImage, overlayColors[0]);
                    log.debug("Colored base item created");
                } else {
                    log.debug("No colors found, copying base item as-is");
                    Graphics2D g = coloredBaseItem.createGraphics();
                    g.drawImage(itemImage, 0, 0, null);
                    g.dispose();
                }
            }
            case MAPPED -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                if (overlayColors != null) {
                    OverlayType.mappedOverlay(coloredBaseItem, itemImage, overlay.getOverlayColorOptions().getMap(), overlayColors);
                } else {
                    Graphics2D g = coloredBaseItem.createGraphics();
                    g.drawImage(itemImage, 0, 0, null);
                    g.dispose();
                }
            }
            case DUAL_LAYER -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                if (overlayColors != null) {
                    OverlayType.normalOverlay(coloredBaseItem, itemImage, overlayColors[1]);
                    OverlayType.normalOverlay(coloredBaseItem, itemImage, overlayColors[0]);
                } else {
                    Graphics2D g = coloredBaseItem.createGraphics();
                    g.drawImage(itemImage, 0, 0, null);
                    g.dispose();
                }
            }
        }

        BufferedImage overlaidItem = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D overlaidItemGraphics = overlaidItem.createGraphics();
        
        overlaidItemGraphics.drawImage(coloredBaseItem, 0, 0, null);
        
        log.debug("Drawing uncolored overlay at position: (0, 0)");
        overlaidItemGraphics.drawImage(overlayImage, 0, 0, null);
        overlaidItemGraphics.dispose();

        return overlaidItem;
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private String data;
        private boolean enchanted;
        private boolean hoverEffect;
        private boolean bigImage;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            this.itemId = itemId
                .replace("minecraft:", "")
                .replace("skull", "player_head");
            return this;
        }

        public MinecraftItemGenerator.Builder withData(String data) {
            this.data = data;
            return this;
        }

        public MinecraftItemGenerator.Builder isEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
            return this;
        }

        public MinecraftItemGenerator.Builder withHoverEffect(boolean hoverEffect) {
            this.hoverEffect = hoverEffect;
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
            return new MinecraftItemGenerator(itemId, data, enchanted, hoverEffect, bigImage);
        }
    }
}
