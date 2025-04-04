package net.hypixel.nerdbot.generator.item;

import lombok.Getter;

import java.awt.image.BufferedImage;
import java.util.List;

@Getter
public class GeneratedObject {

    protected final BufferedImage image;
    private final byte[] gifData;
    private final OutputType outputType;
    @Getter
    private final List<BufferedImage> animationFrames;
    @Getter
    private final int frameDelayMs;

    /**
     * Constructor for static PNGs
     *
     * @param image The image to be used for the PNG output
     */
    public GeneratedObject(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null for PNG output");
        }
        this.image = image;
        this.gifData = null;
        this.outputType = OutputType.PNG;
        this.animationFrames = null; // No frames for static images
        this.frameDelayMs = 0; // No delay for static images
    }

    /**
     * Constructor for animated GIFs
     *
     * @param gifData      The byte array of the GIF data
     * @param frames       The list of frames to be used for the GIF output
     * @param frameDelayMs The delay between frames in milliseconds
     */
    public GeneratedObject(byte[] gifData, List<BufferedImage> frames, int frameDelayMs) {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }

        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty for GIF output");
        }

        if (frameDelayMs <= 0) {
            throw new IllegalArgumentException("Frame delay must be greater than 0 for GIF output");
        }

        this.image = frames.get(0);
        this.gifData = gifData;
        this.outputType = OutputType.GIF;
        this.animationFrames = frames;
        this.frameDelayMs = frameDelayMs;
    }

    /**
     * Check if the generated object is animated
     *
     * @return true if the object is animated (GIF), false otherwise (PNG)
     */
    public boolean isAnimated() {
        return this.outputType == OutputType.GIF;
    }

    /**
     * Enum representing the output type of the generated object.
     */
    public enum OutputType {
        PNG, GIF
    }
}
