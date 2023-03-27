package net.hypixel.nerdbot.generator;

import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class ImageMerger {
    private static final int PADDING = 15; //The amount of space between the head and the description.
    private static final double HEAD_SCALE = 0.9; //The scale in which the head appears to the size of the description.
    private static final double HEAD_RATIO = 0.8666; //The ratio for which the width and height of the head exist.
    private final Graphics2D g2d;
    private final BufferedImage itemDescription, itemHead, finalImage;

    /***
     * Merges two existing images (Minecraft Item Description and a Head)
     * @param itemDescription the item's description
     * @param itemHead the item's visual appearance
     */
    public ImageMerger(BufferedImage itemDescription, BufferedImage itemHead) {
        this.itemDescription = itemDescription;
        this.itemHead = itemHead;

        int width = itemDescription.getWidth() + (int) (itemDescription.getHeight() * HEAD_RATIO) + (PADDING * 3);
        int height = itemDescription.getHeight() + (PADDING * 2);

        this.finalImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        this.g2d = this.finalImage.createGraphics();
    }

    /***
     * Draws the final image onto one image
     */
    public void drawFinalImage() {
        int centerLine = this.finalImage.getHeight() / 2;

        int newHeadHeight = this.itemDescription.getHeight(); //The height that we need from the item desc
        int newHeadWidth = (int) (newHeadHeight * HEAD_RATIO); //The width that we need to calculate from the height

        //Scale the itemHead image to the size of the itemDescription
        BufferedImage scaledImage = new BufferedImage((int) (newHeadWidth * HEAD_SCALE), (int) (newHeadHeight * HEAD_SCALE), BufferedImage.TYPE_INT_ARGB);
        Graphics2D scaledImageGraphics = scaledImage.createGraphics();
        scaledImageGraphics.drawImage(this.itemHead, 0, 0, (int) (newHeadWidth * HEAD_SCALE), (int) (newHeadHeight * HEAD_SCALE), null);
        scaledImageGraphics.dispose();

        this.g2d.drawImage(scaledImage, PADDING, centerLine - scaledImage.getHeight() / 2, null);
        this.g2d.drawImage(this.itemDescription, PADDING * 2 + (int) (newHeadWidth), centerLine - this.itemDescription.getHeight() / 2, null);
    }

    /***
     * Gets the drawn image
     * @return the drawn image
     */
    public BufferedImage getImage() {
        return this.finalImage;
    }
}
