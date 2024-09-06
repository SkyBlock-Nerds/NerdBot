package net.hypixel.nerdbot.generator.builder;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GeneratorImageBuilder {

    private static final int IMAGE_PADDING_PX = 25;

    private final List<Generator> generators;

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
     *
     * @return The final {@link GeneratedObject image}.
     * @throws GeneratorException if the generation fails or times out.
     */
    public GeneratedObject build() {
        try {
            return CompletableFuture.supplyAsync(this::buildInternal)
                .orTimeout(NerdBotApp.getBot().getConfig().getImageGeneratorTimeoutMs(), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    if (exception instanceof TimeoutException) {
                        throw new GeneratorException("Timeout reached while generating image");
                    }

                    throw new GeneratorException(exception.getCause().getMessage());
                })
                .get();
        } catch (InterruptedException | ExecutionException | GeneratorException exception) {
            throw new GeneratorException("An error occurred during image generation: " + exception.getCause().getMessage());
        }
    }

    /**
     * Internal method used to build the final output image from the list of {@link Generator generators}.
     *
     * @return The final {@link GeneratedObject image}.
     */
    private GeneratedObject buildInternal() {
        int totalWidth = 0;
        int maxHeight = 0;

        List<GeneratedObject> generatedObjects = generators.stream().map(Generator::generate).toList();

        for (GeneratedObject generatedObject : generatedObjects) {
            BufferedImage generatedImage = generatedObject.getImage();

            if (generatedImage == null) {
                throw new GeneratorException("Could not generate that image!");
            }

            if (generatedObjects.size() > 1) {
                totalWidth += generatedImage.getWidth() + IMAGE_PADDING_PX;
            } else {
                totalWidth += generatedImage.getWidth();
            }

            maxHeight = Math.max(maxHeight, generatedImage.getHeight());
        }

        BufferedImage finalImage = new BufferedImage(totalWidth, maxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = finalImage.createGraphics();
        int x = 0;

        for (GeneratedObject generatedObject : generatedObjects) {
            BufferedImage generatedImage = generatedObject.getImage();

            if (generatedImage == null) {
                throw new GeneratorException("Could not generate image for " + generatedObject.getClass().getSimpleName() + "!");
            }

            // Calculate the vertical offset to center the image vertically
            int yOffset = (maxHeight - generatedImage.getHeight()) / 2;

            // Draw the image with the calculated vertical offset
            graphics.drawImage(generatedImage, x, yOffset, null);

            // Adjust the x position for the next generator
            x += generatedImage.getWidth() + IMAGE_PADDING_PX;
        }

        graphics.dispose();

        return new GeneratedObject(finalImage);
    }
}
