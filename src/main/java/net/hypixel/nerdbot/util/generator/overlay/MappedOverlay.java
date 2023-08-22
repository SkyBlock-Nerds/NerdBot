package net.hypixel.nerdbot.util.generator.overlay;

import java.awt.image.BufferedImage;
import java.util.HashMap;

public class MappedOverlay extends Overlay {
    private final int[] defaultOverlayColor;
    private final HashMap<Integer, Integer> colorBinding;
    private final HashMap<String, int[]> colorChoices;

    /***
     * Creates an Overlay object which applies color transformations onto an image using a pre-defined color map
     *
     * @param name the name of the overlay
     * @param overlay the image of the overlay
     * @param applyIfNoColor if the overlay should be applied when no valid color is present
     * @param defaultOverlayColor the default color map of the overlay
     * @param colorChoices the color map choices
     * @param colorBinding how the RGB value maps to an index
     */
    public MappedOverlay(String name, BufferedImage overlay, boolean applyIfNoColor, int[] defaultOverlayColor, HashMap<String, int[]> colorChoices, HashMap<Integer, Integer> colorBinding) {
        super(name, overlay, applyIfNoColor);

        this.defaultOverlayColor = defaultOverlayColor;
        this.colorChoices = colorChoices;
        this.colorBinding = colorBinding;
    }

    @Override
    public void applyOverlay(BufferedImage image, String color) {
        // do not apply the overlay if there is no default color or color supplied
        if (color.length() == 0 && !this.applyIfNoColor()) {
            return;
        }

        int[] colorToApply = this.colorChoices.getOrDefault(color.toLowerCase(), defaultOverlayColor);
        this.applyModifierOnImage(image, this.overlay, colorToApply);
    }

    /**
     * Applies an image modifier onto the selected image
     *
     * @param image       the image the modifier is being applied to
     * @param sourceImage the overlay source image
     * @param color       the color map
     */
    protected void applyModifierOnImage(BufferedImage image, BufferedImage sourceImage, int[] color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // checking if the pixel has a color
                int rgb = sourceImage.getRGB(x, y);
                if (((rgb >> 24) & 0xff) == 0) {
                    continue;
                }

                int colorIndex = colorBinding.get(rgb);
                image.setRGB(x, y, color[colorIndex]);
            }
        }
    }
}
