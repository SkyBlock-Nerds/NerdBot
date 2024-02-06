package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;
import net.hypixel.nerdbot.generator.image.MinecraftInventoryImage;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.util.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftInventoryGenerator implements Generator {

    private static final int SLOT_DIMENSIONS = 32;
    private static final File SINGLE_SLOT_FILE = new File("./src/main/resources/minecraft/textures/slot.png");
    public static final BufferedImage SINGLE_SLOT_IMAGE = new BufferedImage(SLOT_DIMENSIONS, SLOT_DIMENSIONS, BufferedImage.TYPE_INT_ARGB);

    static {
        try {
            BufferedImage singleSlotImage = ImageUtil.resizeImage(ImageIO.read(SINGLE_SLOT_FILE), SLOT_DIMENSIONS, SLOT_DIMENSIONS);;
            SINGLE_SLOT_IMAGE.getGraphics().drawImage(singleSlotImage, 0, 0, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final int rows;
    private final int slotsPerRow;

    @Override
    public MinecraftInventoryImage generate() {
        int imageWidth = SINGLE_SLOT_IMAGE.getWidth() * slotsPerRow;
        int imageHeight = SINGLE_SLOT_IMAGE.getHeight() * rows;

        BufferedImage inventoryImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        List<InventoryItem> slots = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int slot = 0; slot < slotsPerRow; slot++) {
                int xCoordinate = slot * SINGLE_SLOT_IMAGE.getWidth();
                int yCoordinate = row * SINGLE_SLOT_IMAGE.getHeight();
                inventoryImage.getGraphics().drawImage(SINGLE_SLOT_IMAGE, xCoordinate, yCoordinate, null);
                int globalSlot = slot + (row * slotsPerRow);
                slots.add(new InventoryItem(globalSlot, slot, row, new ImageCoordinates(xCoordinate, yCoordinate)));
            }
        }

        return new MinecraftInventoryImage(rows, slotsPerRow, slots, inventoryImage);
    }

    public static class Builder implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;

        public MinecraftInventoryGenerator.Builder withRows(int rows) {
            this.rows = rows;
            return this;
        }

        public MinecraftInventoryGenerator.Builder withSlotsPerRow(int slotsPerRow) {
            this.slotsPerRow = slotsPerRow;
            return this;
        }

        @Override
        public MinecraftInventoryGenerator build() {
            return new MinecraftInventoryGenerator(rows, slotsPerRow);
        }
    }
}
