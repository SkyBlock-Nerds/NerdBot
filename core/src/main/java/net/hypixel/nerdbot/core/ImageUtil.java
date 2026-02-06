package net.hypixel.nerdbot.core;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
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

    /**
     * Checks if the image file format is grayscale (1 or 2 color channels).
     * Used to avoid sRGB conversion when reading raw pixels from grayscale PNGs.
     *
     * @param image The image to check
     *
     * @return true if the image format is grayscale
     */
    public static boolean isGrayscaleFormat(BufferedImage image) {
        int numBands = image.getRaster().getNumBands();
        int type = image.getType();

        // Indexed color has 1 band but stores palette indices, not grayscale
        if (type == BufferedImage.TYPE_BYTE_INDEXED) {
            return false;
        }

        // 1 band = grayscale, 2 bands = grayscale + alpha
        return numBands == 1 || numBands == 2;
    }

    /**
     * Checks if an image is grayscale by sampling pixels.
     *
     * @param image The image to check
     *
     * @return true if all sampled non-transparent pixels have R=G=B
     */
    public static boolean isGrayscaleImage(BufferedImage image) {
        int sampleCount = 0;
        int maxSamples = 100;

        for (int y = 0; y < image.getHeight() && sampleCount < maxSamples; y += Math.max(1, image.getHeight() / 10)) {
            for (int x = 0; x < image.getWidth() && sampleCount < maxSamples; x += Math.max(1, image.getWidth() / 10)) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;

                if (alpha == 0) {
                    continue;
                }

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Allow small tolerance
                if (Math.abs(r - g) > 2 || Math.abs(g - b) > 2 || Math.abs(r - b) > 2) {
                    return false;
                }

                sampleCount++;
            }
        }

        return sampleCount > 0;
    }

    /**
     * Converts any image to TYPE_INT_ARGB using Graphics2D, properly handling
     * indexed/palette PNG transparency.
     *
     * @param source The source image to convert
     *
     * @return The converted ARGB image, or the original if already ARGB
     */
    public static BufferedImage convertToArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = converted.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();

        return converted;
    }

    /**
     * Converts any image to ARGB by reading raw raster data. This avoids gamma
     * conversion issues that can occur with getRGB() on grayscale images.
     *
     * @param source The source image to convert
     *
     * @return The converted ARGB image, or the original if already ARGB
     */
    public static BufferedImage convertToArgbRaw(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }

        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] destPixels = ((DataBufferInt) converted.getRaster().getDataBuffer()).getData();

        Raster raster = source.getRaster();
        int numBands = raster.getNumBands();
        int[] pixel = new int[numBands];

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                raster.getPixel(x, y, pixel);

                int a, r, g, b;
                if (numBands == 2) {
                    int gray = pixel[0];
                    a = pixel[1];
                    r = g = b = gray;
                } else if (numBands == 4) {
                    r = pixel[0];
                    g = pixel[1];
                    b = pixel[2];
                    a = pixel[3];
                } else if (numBands == 3) {
                    r = pixel[0];
                    g = pixel[1];
                    b = pixel[2];
                    a = 255;
                } else {
                    destPixels[y * source.getWidth() + x] = source.getRGB(x, y);
                    continue;
                }

                destPixels[y * source.getWidth() + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }

        return converted;
    }

    /**
     * Resizes an image using nearest-neighbor sampling with direct pixel access.
     * Handles grayscale images specially to avoid gamma conversion issues.
     *
     * @param source The source image to resize
     * @param width  The target width
     * @param height The target height
     *
     * @return The resized ARGB image
     */
    public static BufferedImage resizeImageRaw(BufferedImage source, int width, int height) {
        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] destPixels = ((DataBufferInt) result.getRaster().getDataBuffer()).getData();

        if (isGrayscaleFormat(source)) {
            // Read raw samples to preserve linear grayscale values without gamma conversion
            Raster srcRaster = source.getRaster();
            int numBands = srcRaster.getNumBands();
            int[] samples = new int[numBands];

            for (int dstY = 0; dstY < height; dstY++) {
                int srcY = dstY * srcHeight / height;
                for (int dstX = 0; dstX < width; dstX++) {
                    int srcX = dstX * srcWidth / width;
                    srcRaster.getPixel(srcX, srcY, samples);
                    int gray = samples[0];
                    int alpha = numBands == 2 ? samples[1] : 255;
                    destPixels[dstY * width + dstX] = (alpha << 24) | (gray << 16) | (gray << 8) | gray;
                }
            }
        } else {
            BufferedImage argbSource = convertToArgb(source);
            for (int dstY = 0; dstY < height; dstY++) {
                int srcY = dstY * srcHeight / height;
                for (int dstX = 0; dstX < width; dstX++) {
                    int srcX = dstX * srcWidth / width;
                    destPixels[dstY * width + dstX] = argbSource.getRGB(srcX, srcY);
                }
            }
        }

        return result;
    }

    /**
     * Returns true if all pixels in the image are fully transparent.
     *
     * @param image The ARGB image to check
     *
     * @return true if all pixels have zero alpha
     */
    public static boolean isFullyTransparent(BufferedImage image) {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int pixel : pixels) {
            int alpha = (pixel >> 24) & 0xFF;
            if (alpha > 0) {
                return false;
            }
        }
        return true;
    }
}
