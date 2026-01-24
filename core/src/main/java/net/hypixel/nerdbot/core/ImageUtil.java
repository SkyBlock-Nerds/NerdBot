package net.hypixel.nerdbot.core;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ImageUtil {

    /**
     * Saves the image to a file
     *
     * @return a file which can be shared
     *
     * @throws IOException If the file cannot be saved
     */
    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    /**
     * Reads a {@link BufferedImage} from a file.
     *
     * @param file The file to read the image from.
     *
     * @return The {@link BufferedImage} read from the file.
     *
     * @throws IOException If an error occurs while reading the file.
     */
    public static BufferedImage fromFile(File file) throws IOException {
        return ImageIO.read(file);
    }

    /**
     * Upscales the given {@link BufferedImage} by a scaling factor.
     *
     * @param inputImage    The image to be upscaled.
     * @param scalingFactor The factor by which to upscale the image (e.g. 2.0 for double size).
     *
     * @return The upscaled {@link BufferedImage}.
     */
    public static BufferedImage upscaleImage(BufferedImage inputImage, double scalingFactor) {
        int newWidth = (int) (inputImage.getWidth() * scalingFactor);
        int newHeight = (int) (inputImage.getHeight() * scalingFactor);

        int imageType = inputImage.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = BufferedImage.TYPE_INT_ARGB;
        }

        BufferedImage upscaledImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D graphics = upscaledImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(inputImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return upscaledImage;
    }

    /**
     * Downscales the given {@link BufferedImage} by a scaling factor.
     *
     * @param inputImage    The image to be downscaled.
     * @param scalingFactor The factor by which to downscale the image (e.g. 2.0 for half size).
     *
     * @return The downscaled {@link BufferedImage}.
     */
    public static BufferedImage downscaleImage(BufferedImage inputImage, double scalingFactor) {
        int newWidth = (int) (inputImage.getWidth() / scalingFactor);
        int newHeight = (int) (inputImage.getHeight() / scalingFactor);

        int imageType = inputImage.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM) {
            imageType = BufferedImage.TYPE_INT_ARGB;
        }

        BufferedImage downscaledImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D graphics = downscaledImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(inputImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        return downscaledImage;
    }

    /**
     * Resizes the given {@link BufferedImage} to the specified width and height.
     *
     * @param originalImage The original image to resize.
     * @param width         The desired width of the resized image.
     * @param height        The desired height of the resized image.
     *
     * @return The resized {@link BufferedImage}
     */
    public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        return resizeImage(originalImage, width, height, originalImage.getType());
    }

    /**
     * Resizes the given {@link BufferedImage} to the specified width and height.
     *
     * @param originalImage The original image to resize.
     * @param width         The desired width of the resized image.
     * @param height        The desired height of the resized image.
     * @param type          The image type (e.g., BufferedImage.TYPE_INT_RGB).
     *
     * @return The resized {@link BufferedImage}
     */
    public static BufferedImage resizeImage(BufferedImage originalImage, int width, int height, int type) {
        if (type == BufferedImage.TYPE_CUSTOM) {
            type = BufferedImage.TYPE_INT_ARGB;
        }

        BufferedImage resizedImage = new BufferedImage(width, height, type);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();

        return resizedImage;
    }

    /**
     * Encodes a list of BufferedImage frames into an animated GIF byte array using the {@link GifSequenceWriter}
     *
     * @param frames  The list of frames to encode.
     * @param delayMs The delay between frames in milliseconds.
     * @param loop    True if the GIF should loop indefinitely.
     *
     * @return A byte array containing the animated GIF data.
     *
     * @throws IOException If an error occurs during encoding.
     */
    public static byte[] toGifBytes(List<BufferedImage> frames, int delayMs, boolean loop) throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("Frames list cannot be null or empty");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageOutputStream imageOutputStream = null;
        GifSequenceWriter writer = null;

        try {
            int imageType = frames.getFirst().getType();
            if (imageType == BufferedImage.TYPE_CUSTOM) {
                imageType = BufferedImage.TYPE_INT_ARGB;
            }

            imageOutputStream = ImageIO.createImageOutputStream(baos);

            writer = new GifSequenceWriter(imageOutputStream, imageType, delayMs, loop);

            for (BufferedImage frame : frames) {
                writer.writeToSequence(frame);
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    System.err.println("Error closing GifSequenceWriter: " + e.getMessage());
                }
            }

            if (imageOutputStream != null) {
                try {
                    imageOutputStream.close();
                } catch (IOException e) {
                    System.err.println("Error closing ImageOutputStream: " + e.getMessage());
                }
            }
        }

        return baos.toByteArray();
    }

    /**
     * Copies a source image onto an existing target image.
     *
     * @param target Target image to draw onto
     * @param source Source image to copy from
     */
    public static void copyImage(BufferedImage target, BufferedImage source) {
        Graphics2D g = target.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
    }
}
