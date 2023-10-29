package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.util.Item;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.spritesheet.ItemSpritesheet;

import java.awt.image.BufferedImage;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftItemGenerator implements Generator {

    private final String itemId;
    private String extraDetails;

    public MinecraftItemGenerator(String itemId, String extraDetails) {
        this.itemId = itemId;
        this.extraDetails = extraDetails;
    }

    @Override
    public Item generate() {
        BufferedImage image = ItemSpritesheet.getTexture(itemId);

        if (image.getWidth() <= 16 && image.getHeight() <= 16) {
            return new Item(ImageUtil.upscaleImage(image, 20));
        }

        return new Item(ItemSpritesheet.getTexture(itemId));
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private String extraDetails;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public MinecraftItemGenerator.Builder withExtraDetails(String extraDetails) {
            this.extraDetails = extraDetails;
            return this;
        }

        @Override
        public MinecraftItemGenerator build() {
            return new MinecraftItemGenerator(itemId, extraDetails);
        }
    }
}
