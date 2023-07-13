package net.hypixel.nerdbot.generator;

import java.awt.*;
import java.awt.image.BufferedImage;

public class ImageMerger {
    private static final int PADDING = 15; // The amount of space between the head and the description.
    private static final double HEAD_SCALE = 0.7; // The scale in which the head appears to the size of the description.
    private final Graphics2D g2d;
    private final BufferedImage itemDescription;
    private final BufferedImage itemHead;
    private final BufferedImage finalImage;
    private final int headWidth;
    private final int headHeight;

    /***
     * Merges two existing images (Minecraft Item Description and a Head)
     * @param itemDescription the item's description
     * @param itemHead the item's visual appearance
     */
    public ImageMerger(BufferedImage itemDescription, BufferedImage itemHead) {
        this.itemDescription = itemDescription;
        this.itemHead = itemHead;

        double finalMaxHeadHeight = this.itemDescription.getHeight() * HEAD_SCALE;
        if (itemHead.getWidth() == 16) {
            int scale = (int) (finalMaxHeadHeight / 16);
            this.headWidth = scale * 16;
            this.headHeight = scale * 16;
        } else {
            this.headWidth = (int) ((itemHead.getWidth() / (double) itemHead.getHeight()) * finalMaxHeadHeight);
            this.headHeight = (int) finalMaxHeadHeight;
        }

        int width = itemDescription.getWidth() + this.headWidth + (PADDING * 3);
        int height = itemDescription.getHeight() + (PADDING * 2);

        this.finalImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        this.g2d = this.finalImage.createGraphics();
    }

    /***
     * Draws the final image onto one image
     */
    public void drawFinalImage() {
        int centerLine = this.finalImage.getHeight() / 2;

        this.g2d.drawImage(this.itemHead, PADDING, centerLine - this.headHeight / 2, this.headWidth, this.headHeight, null);
        this.g2d.drawImage(this.itemDescription, PADDING * 2 + (this.headWidth), centerLine - this.itemDescription.getHeight() / 2, null);
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
