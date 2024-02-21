package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;
import net.hypixel.nerdbot.generator.image.MinecraftInventoryImage;
import net.hypixel.nerdbot.generator.item.InventorySlot;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.Range;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftInventoryGenerator implements Generator {

    public static final int MAX_ROWS_GENERATED = 10;
    public static final int MAX_COLUMNS_GENERATED = 10;
    public static final int PIXELS_PER_PIXEL = 2;
    public static final int PIXELS_PER_SLOT = 18;
    public static final int SLOT_DIMENSION = PIXELS_PER_SLOT * PIXELS_PER_PIXEL;
    public static final BufferedImage SINGLE_SLOT_IMAGE = new BufferedImage(SLOT_DIMENSION, SLOT_DIMENSION, BufferedImage.TYPE_INT_ARGB);

    static {
        try {
            BufferedImage singleSlotImage = ImageUtil.resizeImage(ImageIO.read(new File("src/main/resources/minecraft/spritesheets/slot.png")), SLOT_DIMENSION, SLOT_DIMENSION);
            SINGLE_SLOT_IMAGE.getGraphics().drawImage(singleSlotImage, 0, 0, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final int rows;
    private final int slotsPerRow;
    private final boolean drawBorder;
    private final boolean drawTitle;
    private final boolean drawBackground;

    @Override
    public MinecraftInventoryImage generate() {
        int offset = drawBorder ? 7 * PIXELS_PER_PIXEL : 0;
        int imageWidth = SLOT_DIMENSION * slotsPerRow + offset * 2;
        int topOffset = offset + (drawTitle ? 13 * PIXELS_PER_PIXEL : 0) - (drawBorder && drawTitle ? 3 * PIXELS_PER_PIXEL : 0);
        int imageHeight = SLOT_DIMENSION * rows + topOffset + offset;

        BufferedImage inventoryImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) inventoryImage.getGraphics();

        if (drawBorder) {
            // drawing the overall background
            g2d.setColor(new Color(198, 198, 198));
            g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, imageWidth - PIXELS_PER_PIXEL * 6, imageHeight - PIXELS_PER_PIXEL * 6);
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right
            g2d.fillRect(PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left

            // drawing the dark gray
            g2d.setColor(new Color(85, 85, 85));
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 4); // vertical right
            g2d.fillRect(PIXELS_PER_PIXEL * 3, imageHeight - PIXELS_PER_PIXEL * 3, imageWidth - PIXELS_PER_PIXEL * 6, PIXELS_PER_PIXEL * 2); // horizontal bottom
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 4, imageHeight - PIXELS_PER_PIXEL * 4, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // square bottom right

            // drawing the white border
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillRect(PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 4); // vertical left
            g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, imageWidth - PIXELS_PER_PIXEL * 6, PIXELS_PER_PIXEL * 2); // horizontal top
            g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // square top left

            g2d.setColor(new Color(0, 0, 0));
            // vertical black lines
            g2d.fillRect(0, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 5); // vertical left
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 5); // vertical right
            // horizontal black lines
            g2d.fillRect(PIXELS_PER_PIXEL * 2, 0, imageWidth - PIXELS_PER_PIXEL * 5, PIXELS_PER_PIXEL); // horizontal top
            g2d.fillRect(PIXELS_PER_PIXEL * 3, imageHeight - PIXELS_PER_PIXEL, imageWidth - PIXELS_PER_PIXEL * 5, PIXELS_PER_PIXEL); // horizontal bottom
            // black corners
            g2d.fillRect(PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top left
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right - upper
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right - lower
            g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom right
            g2d.fillRect(PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left - upper
            g2d.fillRect(PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left - lower
        }

        List<InventorySlot> slots = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            int yCoordinate = row * SLOT_DIMENSION + topOffset;
            for (int slot = 0; slot < slotsPerRow; slot++) {
                int xCoordinate = slot * SLOT_DIMENSION + offset;
                int globalSlot = slot + (row * slotsPerRow);
                slots.add(new InventorySlot(globalSlot, slot, row, new ImageCoordinates(xCoordinate, yCoordinate)));

                if (drawBackground) {
                    g2d.drawImage(SINGLE_SLOT_IMAGE, xCoordinate, yCoordinate, null);
                }
            }
        }

        return new MinecraftInventoryImage(rows, slotsPerRow, slots, inventoryImage);
    }

    public static class Builder implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;
        private boolean drawTitle;
        private boolean drawBorder;
        private boolean drawBackground = true;

        public MinecraftInventoryGenerator.Builder withRows(int rows) {
            this.rows = Range.between(1, MAX_ROWS_GENERATED).fit(rows);
            return this;
        }

        public MinecraftInventoryGenerator.Builder withSlotsPerRow(int slotsPerRow) {
            this.slotsPerRow = Range.between(1, MAX_COLUMNS_GENERATED).fit(slotsPerRow);
            return this;
        }

        public MinecraftInventoryGenerator.Builder drawTitle(boolean drawTitle) {
            this.drawTitle = drawTitle;
            return this;
        }

        public MinecraftInventoryGenerator.Builder drawBorder(boolean drawBorder) {
            this.drawBorder = drawBorder;
            return this;
        }

        public MinecraftInventoryGenerator.Builder drawBackground(boolean drawBackground) {
            this.drawBackground = drawBackground;
            return this;
        }

        @Override
        public MinecraftInventoryGenerator build() {
            return new MinecraftInventoryGenerator(rows, slotsPerRow, drawBorder, drawTitle, drawBackground);
        }
    }
}
