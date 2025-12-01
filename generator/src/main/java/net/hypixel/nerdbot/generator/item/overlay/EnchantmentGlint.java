package net.hypixel.nerdbot.generator.item.overlay;

import net.hypixel.nerdbot.generator.exception.GeneratorException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EnchantmentGlint {

    private static final int FRAME_DELAY_MS = 33; // ~30 FPS for smoother motion
    private static final int TOTAL_DURATION_MS = 6_000;
    private static final int FRAME_COUNT = (int) Math.ceil((double) TOTAL_DURATION_MS / FRAME_DELAY_MS);
    private static final double UV_SCALE = 8.0;
    private static final double PRIMARY_ROTATION_DEG = -50.0;
    private static final double SECONDARY_ROTATION_DEG = 10.0;
    private static final int PRIMARY_PERIOD_MS = 3_000;
    private static final int SECONDARY_PERIOD_MS = 4_875;
    private static final float[] GLINT_TINT = {0.5f, 0.25f, 0.8f};
    private static final float GLINT_INTENSITY = 0.75f;
    private static final double GLINT_SCROLL_SPEED = 0.3;
    private static final double BASE_SPRITE_PIXELS = 16.0;
    private static final BufferedImage GLINT_TEXTURE;

    static {
        try (InputStream stream = EnchantmentGlint.class.getResourceAsStream("/minecraft/assets/textures/glint.png")) {
            if (stream == null) {
                throw new IOException("Missing enchant glint texture: /minecraft/assets/textures/glint.png");
            }
            GLINT_TEXTURE = ImageIO.read(stream);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private EnchantmentGlint() {
    }

    public static GlintAnimation applyEnchantGlint(BufferedImage baseImage) {
        if (baseImage == null) {
            throw new IllegalArgumentException("Base image cannot be null");
        }

        BufferedImage prepared = ensureArgb(baseImage);
        int width = prepared.getWidth();
        int height = prepared.getHeight();
        int[] basePixels = prepared.getRGB(0, 0, width, height, null, 0, width);

        double spriteSpanU = BASE_SPRITE_PIXELS / GLINT_TEXTURE.getWidth();
        double spriteSpanV = BASE_SPRITE_PIXELS / GLINT_TEXTURE.getHeight();
        double resolutionScale = Math.max(Math.max(width, height) / BASE_SPRITE_PIXELS, 1.0);
        double uvScale = UV_SCALE / resolutionScale;

        List<BufferedImage> frames = new ArrayList<>(FRAME_COUNT);
        for (int frameIndex = 0; frameIndex < FRAME_COUNT; frameIndex++) {
            double timeMs = frameIndex * FRAME_DELAY_MS;
            int[] framePixels = Arrays.copyOf(basePixels, basePixels.length);

            applyGlintPass(framePixels, width, height, timeMs, PRIMARY_PERIOD_MS, PRIMARY_ROTATION_DEG, 1.0, spriteSpanU, spriteSpanV, uvScale);
            applyGlintPass(framePixels, width, height, timeMs, SECONDARY_PERIOD_MS, SECONDARY_ROTATION_DEG, -1.0, spriteSpanU, spriteSpanV, uvScale);

            BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            frame.setRGB(0, 0, width, height, framePixels, 0, width);
            frames.add(frame);
        }

        return new GlintAnimation(frames, FRAME_DELAY_MS);
    }

    private static BufferedImage ensureArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        return converted;
    }

    private static void applyGlintPass(int[] pixels,
                                       int width,
                                       int height,
                                       double timeMs,
                                       double periodMs,
                                       double rotationDeg,
                                       double direction,
                                       double spriteSpanU,
                                       double spriteSpanV,
                                       double uvScale) {
        double offset = (timeMs % periodMs) / periodMs;
        double inverseScale = 1.0 / uvScale;
        double adjustedOffset = offset * GLINT_SCROLL_SPEED;
        double radians = Math.toRadians(rotationDeg);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int textureWidth = GLINT_TEXTURE.getWidth();
        int textureHeight = GLINT_TEXTURE.getHeight();
        float[] sampled = new float[4];

        for (int y = 0; y < height; y++) {
            double baseV = (double) y / height * spriteSpanV;
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int argb = pixels[index];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }

                double baseU = (double) x / width * spriteSpanU;

                double rotatedU = baseU * cos - baseV * sin;
                double rotatedV = baseU * sin + baseV * cos;

                double translatedU = rotatedU + direction * adjustedOffset * inverseScale;
                double scaledU = translatedU * uvScale;
                double scaledV = rotatedV * uvScale;

                sampleGlint(scaledU, scaledV, textureWidth, textureHeight, sampled);
                float glintAlpha = sampled[3] * GLINT_INTENSITY;
                if (glintAlpha <= 0.0f) {
                    continue;
                }

                float baseR = ((argb >> 16) & 0xFF) / 255f;
                float baseG = ((argb >> 8) & 0xFF) / 255f;
                float baseB = (argb & 0xFF) / 255f;

                float addR = sampled[0] * GLINT_TINT[0] * glintAlpha;
                float addG = sampled[1] * GLINT_TINT[1] * glintAlpha;
                float addB = sampled[2] * GLINT_TINT[2] * glintAlpha;

                int outR = (int) (Math.min(1.0f, baseR + addR) * 255.0f + 0.5f);
                int outG = (int) (Math.min(1.0f, baseG + addG) * 255.0f + 0.5f);
                int outB = (int) (Math.min(1.0f, baseB + addB) * 255.0f + 0.5f);

                pixels[index] = (alpha << 24) | (outR << 16) | (outG << 8) | outB;
            }
        }
    }

    private static void sampleGlint(double u, double v, int textureWidth, int textureHeight, float[] out) {
        double wrappedU = u - Math.floor(u);
        double wrappedV = v - Math.floor(v);

        double texX = wrappedU * textureWidth - 0.5;
        double texY = wrappedV * textureHeight - 0.5;

        double baseX = Math.floor(texX);
        double baseY = Math.floor(texY);

        int leftX = floorMod((int) baseX, textureWidth);
        int topY = floorMod((int) baseY, textureHeight);
        int rightX = (leftX + 1) % textureWidth;
        int bottomY = (topY + 1) % textureHeight;

        double fracX = texX - baseX;
        double fracY = texY - baseY;

        int topLeftColor = GLINT_TEXTURE.getRGB(leftX, topY);
        int topRightColor = GLINT_TEXTURE.getRGB(rightX, topY);
        int bottomLeftColor = GLINT_TEXTURE.getRGB(leftX, bottomY);
        int bottomRightColor = GLINT_TEXTURE.getRGB(rightX, bottomY);

        double weightTopLeft = (1.0 - fracX) * (1.0 - fracY);
        double weightTopRight = fracX * (1.0 - fracY);
        double weightBottomLeft = (1.0 - fracX) * fracY;
        double weightBottomRight = fracX * fracY;

        double red = ((topLeftColor >> 16) & 0xFF) * weightTopLeft
            + ((topRightColor >> 16) & 0xFF) * weightTopRight
            + ((bottomLeftColor >> 16) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >> 16) & 0xFF) * weightBottomRight;
        double green = ((topLeftColor >> 8) & 0xFF) * weightTopLeft
            + ((topRightColor >> 8) & 0xFF) * weightTopRight
            + ((bottomLeftColor >> 8) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >> 8) & 0xFF) * weightBottomRight;
        double blue = (topLeftColor & 0xFF) * weightTopLeft
            + (topRightColor & 0xFF) * weightTopRight
            + (bottomLeftColor & 0xFF) * weightBottomLeft
            + (bottomRightColor & 0xFF) * weightBottomRight;
        double alpha = ((topLeftColor >>> 24) & 0xFF) * weightTopLeft
            + ((topRightColor >>> 24) & 0xFF) * weightTopRight
            + ((bottomLeftColor >>> 24) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >>> 24) & 0xFF) * weightBottomRight;

        out[0] = (float) (red / 255.0);
        out[1] = (float) (green / 255.0);
        out[2] = (float) (blue / 255.0);
        out[3] = (float) (alpha / 255.0);
    }

    private static int floorMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    public record GlintAnimation(List<BufferedImage> frames, int frameDelayMs) {
        public GlintAnimation(List<BufferedImage> frames, int frameDelayMs) {
            if (frames == null || frames.isEmpty()) {
                throw new GeneratorException("Enchantment glint produced no frames");
            }

            if (frameDelayMs <= 0) {
                throw new GeneratorException("Frame delay must be positive");
            }

            this.frames = List.copyOf(frames);
            this.frameDelayMs = frameDelayMs;
        }

        public BufferedImage firstFrame() {
            return frames.getFirst();
        }

        public boolean isAnimated() {
            return frames.size() > 1;
        }
    }
}
