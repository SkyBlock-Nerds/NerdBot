package net.hypixel.nerdbot.generator.image;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.util.ImageUtil;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Log4j2
public class GeneratorImageBuilder {

    private static final int IMAGE_PADDING_PX = 25;

    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Generator> generators;

    /**
     * Default constructor for the {@link GeneratorImageBuilder} class.
     */
    public GeneratorImageBuilder() {
        this.generators = new ArrayList<>();
    }

    /**
     * Add a {@link Generator} to the list of {@link Generator generators}.
     * The {@link Generator} will be added to the end of the list, and thus will be drawn last
     * or wherever the generator is positioned in the list.
     *
     * @param generator The {@link Generator generator} to add.
     *
     * @return The {@link GeneratorImageBuilder builder} instance.
     */
    public GeneratorImageBuilder addGenerator(Generator generator) {
        this.generators.add(generator);
        return this;
    }

    /**
     * Add a {@link Generator} at a specific position in the list of {@link Generator generators}.
     * The list is 0-indexed.
     *
     * @param position  The position to add the {@link Generator generator} at. 0-indexed.
     * @param generator The {@link Generator generator} to add.
     *
     * @return The {@link GeneratorImageBuilder builder} instance.
     */
    public GeneratorImageBuilder addGenerator(int position, Generator generator) {
        this.generators.add(position, generator);
        return this;
    }

    /**
     * Build the final image from the list of {@link Generator generators}.
     * The image will be generated in the order that the generators were added.
     * If the generation fails or times out, a {@link GeneratorException} will be thrown.
     *
     * @return The final {@link GeneratedObject image}.
     *
     * @throws GeneratorException if the generation fails or times out.
     */
    public GeneratedObject build() {
        Future<GeneratedObject> future = executorService.submit(this::buildInternal);
        long timeoutMs = NerdBotApp.getBot().getConfig().getImageGeneratorTimeoutMs();

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new GeneratorException("Image generation timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Image generation was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new GeneratorException("An error occurred during image generation", exception.getCause());
        }
    }

    /**
     * Internal method used to build the final output image from the list of {@link Generator generators}.
     *
     * @return The final {@link GeneratedObject image}.
     */
    private GeneratedObject buildInternal() throws GeneratorException {
        // Generate all objects first
        List<GeneratedObject> generatedObjects = new ArrayList<>();
        for (Generator generator : generators) {
            try {
                generatedObjects.add(generator.generate());
            } catch (Exception e) {
                throw new GeneratorException("Error generating object from generator: " + e.getMessage(), e);
            }
        }

        boolean isAnimated = generatedObjects.stream().anyMatch(GeneratedObject::isAnimated);

        if (isAnimated) {
            try {
                return buildAnimatedInternal(generatedObjects);
            } catch (IOException e) {
                log.error("Failed to build animated GIF", e);
                throw new GeneratorException("Failed to build animated GIF: " + e.getMessage(), e);
            }
        } else {
            return buildStaticInternal(generatedObjects);
        }
    }

    /**
     * Builds a static composite image from non-animated GeneratedObjects.
     */
    private GeneratedObject buildStaticInternal(List<GeneratedObject> generatedObjects) throws GeneratorException {
        if (generatedObjects.isEmpty()) {
            throw new GeneratorException("No generators provided to build an image.");
        }

        int totalWidth = 0;
        int maxHeight = 0;

        // Calculate dimensions
        for (int i = 0; i < generatedObjects.size(); i++) {
            GeneratedObject generatedObject = generatedObjects.get(i);
            BufferedImage generatedImage = generatedObject.getImage();

            if (generatedImage == null) {
                throw new GeneratorException("Generated image is null for " + generatedObject.getClass().getSimpleName());
            }

            totalWidth += generatedImage.getWidth();
            if (i < generatedObjects.size() - 1) { // Add padding except for the last image
                totalWidth += IMAGE_PADDING_PX;
            }
            maxHeight = Math.max(maxHeight, generatedImage.getHeight());
        }

        if (totalWidth <= 0 || maxHeight <= 0) {
            throw new GeneratorException("Calculated image dimensions are invalid (width=" + totalWidth + ", height=" + maxHeight + ")");
        }

        BufferedImage finalImage = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = finalImage.createGraphics();
        int currentX = 0;

        // Draw images
        for (GeneratedObject generatedObject : generatedObjects) {
            BufferedImage generatedImage = generatedObject.getImage();
            int yOffset = (maxHeight - generatedImage.getHeight()) / 2; // Center vertically
            graphics.drawImage(generatedImage, currentX, yOffset, null);
            currentX += generatedImage.getWidth() + IMAGE_PADDING_PX;
        }

        graphics.dispose();
        return new GeneratedObject(finalImage);
    }

    /**
     * Builds an animated GIF from {@link GeneratedObject} instances.
     */
    private GeneratedObject buildAnimatedInternal(List<GeneratedObject> generatedObjects) throws IOException, GeneratorException {
        if (generatedObjects.isEmpty()) {
            throw new GeneratorException("No generators provided to build an image.");
        }

        int maxFrames = 1;
        int frameDelayMs = 50; // Default delay
        boolean delaySet = false;

        // Find max frames and first delay
        for (GeneratedObject obj : generatedObjects) {
            if (obj.isAnimated()) {
                maxFrames = Math.max(maxFrames, obj.getAnimationFrames().size());
                if (!delaySet && obj.getFrameDelayMs() > 0) {
                    frameDelayMs = obj.getFrameDelayMs();
                    delaySet = true;
                }
            }
        }

        // Calculate dimensions
        int totalWidth = 0;
        int maxHeight = 0;
        for (int i = 0; i < generatedObjects.size(); i++) {
            GeneratedObject generatedObject = generatedObjects.get(i);
            BufferedImage frameImage = generatedObject.getImage(); // Use the stored first frame/static image

            if (frameImage == null) {
                throw new GeneratorException("Generated image (frame 0) is null for " + generatedObject.getClass().getSimpleName());
            }

            totalWidth += frameImage.getWidth();
            if (i < generatedObjects.size() - 1) { // Add padding except for the last image
                totalWidth += IMAGE_PADDING_PX;
            }
            maxHeight = Math.max(maxHeight, frameImage.getHeight());
        }

        if (totalWidth <= 0 || maxHeight <= 0) {
            throw new GeneratorException("Calculated animated image dimensions are invalid (width=" + totalWidth + ", height=" + maxHeight + ")");
        }

        List<BufferedImage> compositeFrames = new ArrayList<>();

        // Generate each frame
        for (int frameIndex = 0; frameIndex < maxFrames; frameIndex++) {
            BufferedImage compositeFrame = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = compositeFrame.createGraphics();
            int currentX = 0;

            for (GeneratedObject obj : generatedObjects) {
                BufferedImage currentFrameImage;

                if (obj.isAnimated()) {
                    List<BufferedImage> frames = obj.getAnimationFrames();

                    if (frames == null || frames.isEmpty()) {
                        throw new GeneratorException("Animated object has null or empty frames list: " + obj.getClass().getSimpleName());
                    }
                    currentFrameImage = frames.get(frameIndex % frames.size());
                } else {
                    currentFrameImage = obj.getImage();
                }

                int yOffset = (maxHeight - currentFrameImage.getHeight()) / 2;
                graphics.drawImage(currentFrameImage, currentX, yOffset, null);
                currentX += currentFrameImage.getWidth() + IMAGE_PADDING_PX;
            }

            graphics.dispose();
            compositeFrames.add(compositeFrame);
        }

        byte[] gifData = ImageUtil.toGifBytes(compositeFrames, frameDelayMs, true);

        return new GeneratedObject(gifData, compositeFrames, frameDelayMs);
    }
}
