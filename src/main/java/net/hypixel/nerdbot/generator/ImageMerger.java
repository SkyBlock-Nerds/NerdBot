package net.hypixel.nerdbot.generator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageMerger {
    private static final int PADDING = 20;
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

        int width = itemDescription.getWidth() + itemHead.getWidth() + PADDING * 3;
        int height = Math.max(itemDescription.getHeight(), itemHead.getHeight()) + PADDING * 2;

        this.finalImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR_PRE);
        this.g2d = this.finalImage.createGraphics();
    }

    /***
     * Draws the final image onto one image
     */
    public void drawFinalImage() {
        int centerLine = this.finalImage.getHeight() / 2;
        this.g2d.drawImage(this.itemHead, PADDING, centerLine - this.itemHead.getHeight() / 2, null);
        this.g2d.drawImage(this.itemDescription, PADDING * 2 + this.itemHead.getWidth(), centerLine - this.itemDescription.getHeight() / 2, null);
    }

    /***
     * Gets the drawn image
     * @return the drawn image
     */
    private BufferedImage getImage() {
        return this.finalImage;
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public File toFile() throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(this.getImage(), "PNG", tempFile);
        return tempFile;
    }
}
