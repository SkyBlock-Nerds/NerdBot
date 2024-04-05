package net.hypixel.nerdbot.generator.builder;

import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class GeneratorImageBuilder {

    private static final int IMAGE_PADDING_PX = 25;

    private final List<Generator> generators;

    public GeneratorImageBuilder() {
        this.generators = new ArrayList<>();
    }

    public GeneratorImageBuilder addGenerator(Generator generator) {
        this.generators.add(generator);
        return this;
    }

    public GeneratorImageBuilder addGenerator(int position, Generator generator) {
        this.generators.add(position, generator);
        return this;
    }

    public GeneratedObject build() {
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
                throw new GeneratorException("Could not generate that image!");
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
