package net.hypixel.nerdbot.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageUtil {

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    public static BufferedImage fromFile(File file) throws IOException {
        return ImageIO.read(file);
    }

    public static BufferedImage upscaleImage(BufferedImage inputImage, double scalingFactor) {
        int newWidth = (int) (inputImage.getWidth() * scalingFactor);
        int newHeight = (int) (inputImage.getHeight() * scalingFactor);

        BufferedImage upscaledImage = new BufferedImage(newWidth, newHeight, inputImage.getType());
        Graphics2D graphics = upscaledImage.createGraphics();
        graphics.drawImage(inputImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return upscaledImage;
    }

    public static BufferedImage downscaleImage(BufferedImage inputImage, double scalingFactor) {
        int newWidth = (int) (inputImage.getWidth() / scalingFactor);
        int newHeight = (int) (inputImage.getHeight() / scalingFactor);

        BufferedImage downscaledImage = new BufferedImage(newWidth, newHeight, inputImage.getType());
        Graphics2D graphics = downscaledImage.createGraphics();
        graphics.drawImage(inputImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return downscaledImage;
    }
}
