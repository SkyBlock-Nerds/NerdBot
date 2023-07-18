package net.hypixel.nerdbot.generator;

import java.awt.*;
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

    /***
     * Merges two existing images (Minecraft Item Description and a Head)
     * @param itemDescription the item's description
     * @param itemHead the item's visual appearance
     */
    public ImageMerger(BufferedImage itemDescription, BufferedImage itemHead, BufferedImage itemRecipe) {
        this.itemDescription = itemDescription;
        this.itemHead = itemHead;
        this.itemRecipe = itemRecipe;

        int height = 0;
        int width = 0;
        double finalScale;
        if (itemHead != null && itemRecipe != null) {
            finalScale = Math.min(1.1f, (4f / ((this.itemDescription.getHeight() / 23f) + 7)) + 0.9) * 0.5;
            height += PADDING;
        } else {
            finalScale = Math.min(HEAD_SCALE, (4f / ((this.itemDescription.getHeight() / 23f) + 7)) + 0.6);
        }
        double finalMaxHeadHeight = this.itemDescription.getHeight() * finalScale;

        headDimensions = itemHead != null ? performScale(finalMaxHeadHeight, itemHead.getWidth(), itemHead.getHeight()) : new int[] {0, 0};
        recipeDimensions = itemRecipe != null ? performScale(finalMaxHeadHeight, itemRecipe.getWidth(), itemRecipe.getHeight()) : new int[] {0, 0};

        width += itemDescription.getWidth() + Math.max(headDimensions[0], recipeDimensions[0]) + (PADDING * 3);
        height += Math.max(headDimensions[1] + recipeDimensions[1], itemDescription.getHeight()) + (PADDING * 2);

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

        return new int[] {width, height};
    }

    /***
     * Draws the final image onto one image
     */
    public void drawFinalImage() {
        int centerLine = this.finalImage.getHeight() / 2;
        int verticalCenterLine = Math.max(this.headDimensions[0], this.recipeDimensions[0]) / 2 + PADDING;

        if (this.itemHead != null && this.itemRecipe != null) {
            this.g2d.drawImage(this.itemHead, verticalCenterLine - this.headDimensions[0] / 2, PADDING, this.headDimensions[0], this.headDimensions[1], null);
            this.g2d.drawImage(this.itemRecipe, verticalCenterLine - this.recipeDimensions[0] / 2, centerLine + PADDING / 2, this.recipeDimensions[0], this.recipeDimensions[1], null);
        } else {
            int[] dimensions = this.itemHead != null ? this.headDimensions : this.recipeDimensions;
            this.g2d.drawImage(this.itemHead != null ? this.itemHead : this.itemRecipe, PADDING, centerLine - dimensions[0] / 2, dimensions[0], dimensions[1], null);
        }
        this.g2d.drawImage(this.itemDescription, 2 * verticalCenterLine, centerLine - this.itemDescription.getHeight() / 2, null);
        this.g2d.dispose();
    }

    /***
     * Gets the drawn image
     * @return the drawn image
     */
    public BufferedImage getImage() {
        return this.finalImage;
    }
}
