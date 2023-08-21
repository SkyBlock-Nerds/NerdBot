package net.hypixel.nerdbot.util.generator.overlay;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;

public class NormalOverlay extends Overlay {
    private final Color defaultOverlayColor;
    private final HashMap<String, Color> colorChoices;

    public NormalOverlay(String name, BufferedImage overlay, boolean applyIfNoColor, Color defaultOverlayColor, HashMap<String, Color> colorChoices) {
        super(name, overlay, applyIfNoColor);

        this.defaultOverlayColor = defaultOverlayColor;
        this.colorChoices = colorChoices;
    }

    @Override
    public void applyColor(BufferedImage image, String color) {
        // do not apply the overlay if there is no default color or color supplied
        if (color.length() == 0 && !this.applyIfNoColor()) {
            return;
        }

        Color colorToApply;
        if (color.contains("#")) {
            colorToApply = tryParseColor(color, this.defaultOverlayColor);
        } else {
            colorToApply = this.colorChoices.getOrDefault(color.toLowerCase(), defaultOverlayColor);
        }

        this.applyColorModifierOnImage(image, this.overlay, colorToApply);
    }
}
