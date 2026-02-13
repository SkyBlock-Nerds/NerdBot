package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.generator.GenerationContext;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.effect.EffectContext;
import net.hypixel.nerdbot.generator.effect.EffectPipeline;
import net.hypixel.nerdbot.generator.effect.impl.DurabilityBarEffect;
import net.hypixel.nerdbot.generator.effect.impl.GlintImageEffect;
import net.hypixel.nerdbot.generator.effect.impl.HoverImageEffect;
import net.hypixel.nerdbot.generator.effect.impl.OverlayApplicationEffect;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.spritesheet.OverlayLoader;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftItemGenerator implements Generator {

    private final String itemId;
    private final String data;
    private final String color;
    private final boolean enchanted;
    private final boolean hoverEffect;
    private final boolean bigImage;
    private final Integer durabilityPercent;
    private final OverlayLoader overlayLoader;
    private final EffectPipeline effectPipeline;

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering item '{}' ({})", itemId, this);

        // Load base item texture
        BufferedImage itemImage = Spritesheet.getTexture(itemId.toLowerCase());

        if (itemImage == null) {
            throw new GeneratorException("Item with ID `%s` not found", itemId);
        }

        // Create initial effect context
        EffectContext.Builder contextBuilder = new EffectContext.Builder()
            .withImage(itemImage)
            .withItemId(itemId)
            .withEnchanted(enchanted)
            .withHovered(hoverEffect)
            .putMetadata("data", data)
            .putMetadata("color", color);

        if (durabilityPercent != null) {
            contextBuilder.putMetadata("durabilityPercent", durabilityPercent);
        }

        EffectContext context = contextBuilder.build();

        // Execute effect pipeline (overlay, glint, hover, durability)
        context = effectPipeline.execute(context);

        // Apply big image scaling if requested
        BufferedImage finalImage = context.getImage();
        if (bigImage && finalImage != null && finalImage.getHeight() <= 16 && finalImage.getWidth() <= 16) {
            if (context.isAnimated()) {
                List<BufferedImage> scaledFrames = context.getAnimationFrames().stream()
                    .map(frame -> ImageUtil.upscaleImage(frame, 10))
                    .toList();
                context = new EffectContext.Builder()
                    .withAnimationFrames(scaledFrames, context.getFrameDelayMs())
                    .withItemId(itemId)
                    .withEnchanted(enchanted)
                    .withHovered(hoverEffect)
                    .withMetadata(context.getMetadata())
                    .build();
                finalImage = scaledFrames.getFirst();
            } else {
                finalImage = ImageUtil.upscaleImage(finalImage, 10);
            }
        }

        if (context.isAnimated()) {
            try {
                byte[] gifData = ImageUtil.toGifBytes(
                    context.getAnimationFrames(),
                    context.getFrameDelayMs(),
                    true
                );
                log.debug("Rendered animated item '{}' ({} frames, delay {}ms)",
                    itemId, context.getAnimationFrames().size(), context.getFrameDelayMs());
                return new GeneratedObject(gifData, context.getAnimationFrames(), context.getFrameDelayMs());
            } catch (IOException e) {
                throw new GeneratorException("Failed to encode animation", e);
            }
        }

        log.debug("Rendered static item '{}' (dimensions {}x{})",
            itemId, finalImage.getWidth(), finalImage.getHeight());
        return new GeneratedObject(finalImage);
    }

    public static class Builder implements ClassBuilder<MinecraftItemGenerator> {
        private String itemId;
        private String data;
        private String color;
        private boolean enchanted;
        private boolean hoverEffect;
        private boolean bigImage;
        private Integer durabilityPercent;
        private OverlayLoader overlayLoader;
        private EffectPipeline effectPipeline;

        public MinecraftItemGenerator.Builder withItem(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            this.itemId = itemId.replace("minecraft:", "");
            return this;
        }

        public MinecraftItemGenerator.Builder withData(String data) {
            this.data = data;
            return this;
        }

        public MinecraftItemGenerator.Builder withColor(String color) {
            this.color = color;
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
            if (durabilityPercent < 0 || durabilityPercent > 100) {
                throw new IllegalArgumentException("durabilityPercent must be between 0 and 100");
            }
            this.durabilityPercent = durabilityPercent;
            return this;
        }

        /**
         * Inject custom overlay loader
         *
         * @param loader Overlay loader
         * @return This builder
         */
        public MinecraftItemGenerator.Builder withOverlayLoader(OverlayLoader loader) {
            this.overlayLoader = loader;
            return this;
        }

        /**
         * Inject custom effect pipeline
         *
         * @param pipeline Effect pipeline
         * @return This builder
         */
        public MinecraftItemGenerator.Builder withEffectPipeline(EffectPipeline pipeline) {
            this.effectPipeline = pipeline;
            return this;
        }

        @Override
        public MinecraftItemGenerator build() {
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }

            // Use default overlay loader if not provided
            if (overlayLoader == null) {
                overlayLoader = OverlayLoader.getInstance();
            }

            // Build default effect pipeline if not provided
            if (effectPipeline == null) {
                effectPipeline = buildDefaultEffectPipeline();
            }

            return new MinecraftItemGenerator(
                itemId, data, color, enchanted, hoverEffect, bigImage,
                durabilityPercent, overlayLoader, effectPipeline
            );
        }

        private EffectPipeline buildDefaultEffectPipeline() {
            BufferedImage glintTexture = loadGlintTexture();

            return new EffectPipeline.Builder()
                .addEffect(new OverlayApplicationEffect(overlayLoader))
                .addEffect(new GlintImageEffect(glintTexture))
                .addEffect(new HoverImageEffect())
                .addEffect(new DurabilityBarEffect())
                .build();
        }

        private BufferedImage loadGlintTexture() {
            try (InputStream stream = getClass().getResourceAsStream("/minecraft/assets/textures/glint.png")) {
                if (stream == null) {
                    throw new IOException("glint.png not found");
                }
                return ImageIO.read(stream);
            } catch (IOException e) {
                throw new GeneratorException("Failed to load glint texture", e);
            }
        }
    }
}
