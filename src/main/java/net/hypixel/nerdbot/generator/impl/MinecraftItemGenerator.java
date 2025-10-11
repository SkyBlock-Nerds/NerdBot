package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.cache.GeneratorCache;
import net.hypixel.nerdbot.generator.data.ArmorType;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.overlay.EnchantmentGlint;
import net.hypixel.nerdbot.generator.item.overlay.HoverEffect;
import net.hypixel.nerdbot.generator.item.overlay.ItemOverlay;
import net.hypixel.nerdbot.generator.item.overlay.OverlayType;
import net.hypixel.nerdbot.generator.spritesheet.OverlaySheet;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.generator.validation.ValidationUtils;
import net.hypixel.nerdbot.util.ImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftItemGenerator implements Generator {

    private final String itemId;
    private final String data;
    private final boolean enchanted;
    private final boolean hoverEffect;
    private final boolean bigImage;
    private final Integer durabilityPercent;

    private BufferedImage itemImage;

    @Override
    public GeneratedObject generate() {
        String cacheKey = this.toString();

        BufferedImage cachedImage = GeneratorCache.getImage(cacheKey);
        if (cachedImage != null) {
            log.debug("Using cached image for item: {}", itemId);
            return new GeneratedObject(cachedImage);
        }

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

        if (durabilityPercent != null && shouldShowDurabilityBar()) {
            itemImage = addDurabilityBar(itemImage);
        }

        GeneratorCache.putImage(this.toString(), itemImage);

        return new GeneratedObject(itemImage);
    }

    private BufferedImage applyOverlay(ItemOverlay overlay) {
        BufferedImage overlayImage = overlay.getImage();
        String options = (data != null ? data : "");
        log.debug("Overlay type: {}, Color options: {}, Data: {}", overlay.getType(), overlay.getOverlayColorOptions(), options);
        
        log.debug("Processing overlay with name: '{}'", overlay.getName());
        log.debug("Overlay image dimensions: {}x{}, Base image dimensions: {}x{}", 
                 overlayImage.getWidth(), overlayImage.getHeight(),
                 itemImage.getWidth(), itemImage.getHeight());

        boolean isColorableArmor = ArmorType.isColorableArmor(overlay.getName());

        BufferedImage coloredImage;
        BufferedImage baseForFinal;
        BufferedImage overlayForFinal;

        if (isColorableArmor) {
            coloredImage = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            applyColoring(coloredImage, itemImage, overlay, options);
            baseForFinal = coloredImage;
            overlayForFinal = overlayImage;
            log.debug("Applied coloring to base item for colorable armor");
        } else {
            // For other overlays: color the overlay, leave base item uncolored
            coloredImage = new BufferedImage(overlayImage.getWidth(), overlayImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            applyColoring(coloredImage, overlayImage, overlay, options);
            baseForFinal = itemImage; // Uncolored base
            overlayForFinal = coloredImage; // Colored overlay
            log.debug("Applied coloring to overlay for non-colorable item");
        }

        BufferedImage overlaidItem = new BufferedImage(itemImage.getWidth(), itemImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D overlaidItemGraphics = overlaidItem.createGraphics();
        
        overlaidItemGraphics.drawImage(baseForFinal, 0, 0, null);
        log.debug("Drawing overlay on top. Overlay dimensions: {}x{}, Base dimensions: {}x{}", 
                 overlayForFinal.getWidth(), overlayForFinal.getHeight(),
                 baseForFinal.getWidth(), baseForFinal.getHeight());
        overlaidItemGraphics.drawImage(overlayForFinal, 0, 0, null);
        overlaidItemGraphics.dispose();

        return overlaidItem;
    }

    private void applyColoring(BufferedImage target, BufferedImage source, ItemOverlay overlay, String options) {
        switch (overlay.getType()) {
            case NORMAL -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                log.debug("Retrieved overlay colors: {}", overlayColors != null ? Arrays.toString(overlayColors) : "null");
                if (overlayColors != null) {
                    log.debug("Applying color {} to image", Integer.toHexString(overlayColors[0]));
                    OverlayType.normalOverlay(target, source, overlayColors[0]);
                    log.debug("Colored image created");
                } else {
                    log.debug("No colors found, copying image as-is");
                    Graphics2D g = target.createGraphics();
                    g.drawImage(source, 0, 0, null);
                    g.dispose();
                }
            }
            case MAPPED -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                if (overlayColors != null) {
                    OverlayType.mappedOverlay(target, source, overlay.getOverlayColorOptions().getMap(), overlayColors);
                } else {
                    Graphics2D g = target.createGraphics();
                    g.drawImage(source, 0, 0, null);
                    g.dispose();
                }
            }
            case DUAL_LAYER -> {
                int[] overlayColors = overlay.getOverlayColorOptions().getColorsFromOption(options);
                if (overlayColors != null) {
                    OverlayType.normalOverlay(target, source, overlayColors[1]);
                    OverlayType.normalOverlay(target, source, overlayColors[0]);
                } else {
                    Graphics2D g = target.createGraphics();
                    g.drawImage(source, 0, 0, null);
                    g.dispose();
                }
            }
        }
    }

    private boolean shouldShowDurabilityBar() {
        return durabilityPercent < 100;
    }

    private BufferedImage addDurabilityBar(BufferedImage item) {
        BufferedImage result = new BufferedImage(item.getWidth(), item.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        
        // Draw the original item
        g2d.drawImage(item, 0, 0, null);
        
        // The durability display is two separate bars stacked vertically
        int scaleFactor = item.getWidth() / 16;
        int barWidth = item.getWidth() - (4 * scaleFactor) + scaleFactor;
        int barHeight = scaleFactor;
        int barX = 2 * scaleFactor;
        
        // Position the bars
        int colorBarY = item.getHeight() - (3 * scaleFactor); // Top bar (colored durability)
        int blackBarY = item.getHeight() - (2 * scaleFactor); // Bottom bar (permanently black)
        
        // Draw the bottom bar
        g2d.setColor(Color.BLACK);
        g2d.fillRect(barX, blackBarY, barWidth, barHeight);
        
        // Draw the top durability bar
        g2d.setColor(Color.BLACK);
        g2d.fillRect(barX, colorBarY, barWidth, barHeight);
        
        // Draw the colored portion
        if (durabilityPercent > 0) {
            int filledWidth = (int) (barWidth * durabilityPercent / 100.0);
            Color barColor = getDurabilityColor(durabilityPercent);
            g2d.setColor(barColor);
            g2d.fillRect(barX, colorBarY, filledWidth, barHeight);
        }
        
        g2d.dispose();
        return result;
    }

    private Color getDurabilityColor(int durabilityPercent) {
        if (durabilityPercent > 50) {
            // Green to yellow (high to medium durability)
            int red = (int) (255 * 2 * (100 - durabilityPercent) / 100.0);
            return new Color(red, 255, 0);
        } else {
            // Yellow to red (medium to low durability)
            int green = (int) (255 * 2 * durabilityPercent / 100.0);
            return new Color(255, green, 0);
        }
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private String data;
        private boolean enchanted;
        private boolean hoverEffect;
        private boolean bigImage;
        private Integer durabilityPercent;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            ValidationUtils.requireNonBlank(itemId, "itemId");
            this.itemId = itemId.replace("minecraft:", "");
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

        public MinecraftItemGenerator.Builder withDurability(int durabilityPercent) {
            ValidationUtils.requireInRange(durabilityPercent, 0, 100, "durabilityPercent");
            this.durabilityPercent = durabilityPercent;
            return this;
        }

        @Override
        public MinecraftItemGenerator build() {
            ValidationUtils.requireNonBlank(itemId, "itemId");
            return new MinecraftItemGenerator(itemId, data, enchanted, hoverEffect, bigImage, durabilityPercent);
        }
    }
}
