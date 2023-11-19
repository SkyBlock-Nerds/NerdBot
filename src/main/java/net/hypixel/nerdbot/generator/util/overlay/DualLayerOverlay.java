package net.hypixel.nerdbot.generator.util.overlay;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public class DualLayerOverlay extends Overlay {
    private final Color defaultBaseColor;
    private final Color defaultOverlayColor;
    private final HashMap<String, Color[]> colorChoices;

    /***
     * Creates an Overlay object which applies color transformations onto an image
     *
     * @param name the name of the overlay
     * @param overlay the image of the overlay
     * @param applyIfNoColor if the overlay should be applied when no valid color is present
     */
    public DualLayerOverlay(String name, BufferedImage overlay, boolean applyIfNoColor, Color defaultBaseColor, Color defaultOverlayColor, HashMap<String, Color[]> colorChoices) {
        super(name, overlay, applyIfNoColor);

        this.defaultBaseColor = defaultBaseColor;
        this.defaultOverlayColor = defaultOverlayColor;
        this.colorChoices = colorChoices;
    }

    @Override
    public void applyOverlay(BufferedImage image, String color) {
        // do not apply the overlay if there is no default color or color supplied
        if (color.length() == 0 && !this.applyIfNoColor()) {
            return;
        }

        Color baseColor;
        Color overlayColor;
        if (color.contains("#")) {
            String[] colorSplit = color.split("[^0-9a-fA-F#]");
            overlayColor = colorSplit.length > 0 ? tryParseColor(colorSplit[0], this.defaultBaseColor) : this.defaultBaseColor;
            baseColor = colorSplit.length > 1 ? tryParseColor(colorSplit[1], this.defaultBaseColor) : this.defaultBaseColor;
        } else {
            Color[] selectedColors = this.colorChoices.get(color.toLowerCase());
            if (selectedColors == null) {
                overlayColor = this.defaultOverlayColor;
                baseColor = this.defaultOverlayColor;
            } else {
                overlayColor = selectedColors[0];
                baseColor = selectedColors[1];
            }
        }

        this.applyColorModifierOnImage(image, image, baseColor);
        this.applyColorModifierOnImage(image, this.overlay, overlayColor);
    }
}
