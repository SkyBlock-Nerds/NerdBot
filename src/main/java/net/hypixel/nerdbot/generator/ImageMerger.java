package net.hypixel.nerdbot.generator;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ImageMerger {
    private static final int PADDING = 15; // The amount of space between the head and the description.
    private static final double HEAD_SCALE = 0.9; // The scale in which the head appears to the size of the description.
    private final Graphics2D g2d;
    private final BufferedImage itemDescription;
    private final BufferedImage itemHead;
    private final BufferedImage itemRecipe;
    private final BufferedImage finalImage;
    private final int[] headDimensions;
    private final int[] recipeDimensions;

    /**
     * Merges two existing images (Minecraft Item Description and a Head)
     *
     * @param itemDescription the item's description from {@link MinecraftImage}
     * @param itemHead        the item's visual appearance
     * @param itemRecipe      the item's recipe from {@link MinecraftInventory}
     */
    public ImageMerger(BufferedImage itemDescription, BufferedImage itemHead, BufferedImage itemRecipe) {
        this.itemDescription = itemDescription;
        this.itemHead = itemHead;
        this.itemRecipe = itemRecipe;

        int width = PADDING;
        int maxHeight;
        double finalScale;
        if (itemDescription == null) {
            maxHeight = Math.max(itemHead != null ? itemHead.getHeight() : 0, itemRecipe != null ? itemRecipe.getHeight() : 0);
            finalScale = 1;
        } else {
            maxHeight = this.itemDescription.getHeight();
            finalScale = Math.min(HEAD_SCALE, (4f / ((this.itemDescription.getHeight() / 23f) + 7)) + 0.6);
            width += PADDING + this.itemDescription.getWidth();
        }

        double finalMaxHeadHeight = maxHeight * finalScale;
        headDimensions = itemHead != null ? performScale(finalMaxHeadHeight, itemHead.getWidth(), itemHead.getHeight()) : new int[]{0, 0};
        recipeDimensions = itemRecipe != null ? performScale(finalMaxHeadHeight, itemRecipe.getWidth(), itemRecipe.getHeight()) : new int[]{0, 0};

        int height = Math.max(maxHeight, headDimensions[1]) + (PADDING * 2);
        width += headDimensions[0] + recipeDimensions[0];
        if (itemHead != null) {
            width += PADDING;
        }
        if (itemRecipe != null) {
            width += PADDING;
        }

        this.finalImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        this.g2d = this.finalImage.createGraphics();
    }

    private int[] performScale(double targetMaxHeight, int imageWidth, int imageHeight) {
        int width, height;
        if (imageWidth == 16) {
            int scale = (int) (targetMaxHeight / 16);
            width = scale * 16;
            height = scale * 16;
        } else {
            width = (int) ((imageWidth / (double) imageHeight) * targetMaxHeight);
            height = (int) targetMaxHeight;
        }

        return new int[]{width, height};
    }

    /**
     * Draws the final image onto one image
     */
    public void drawFinalImage() {
        int centerLine = this.finalImage.getHeight() / 2;
        int xPosition = PADDING;

        if (this.itemRecipe != null) {
            this.g2d.drawImage(this.itemRecipe, xPosition, centerLine - this.recipeDimensions[1] / 2, this.recipeDimensions[0], this.recipeDimensions[1], null);
            xPosition += this.recipeDimensions[0] + PADDING;
        }

        if (this.itemHead != null) {
            this.g2d.drawImage(this.itemHead, xPosition, centerLine - this.headDimensions[1] / 2, this.headDimensions[0], this.headDimensions[1], null);
            xPosition += this.headDimensions[0] + PADDING;
        }

        if (this.itemDescription != null) {
            this.g2d.drawImage(this.itemDescription, xPosition, centerLine - this.itemDescription.getHeight() / 2, null);
        }
        this.g2d.dispose();
    }

    /**
     * Gets the drawn image
     *
     * @return the drawn image
     */
    public BufferedImage getImage() {
        return this.finalImage;
    }
}
