package net.hypixel.nerdbot.generator.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.ColorSegment;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.generator.util.MinecraftFonts;
import net.hypixel.nerdbot.marmalade.Range;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class MinecraftTooltip {

    private static final Map<Integer, Map<Integer, List<Character>>> OBFUSCATION_WIDTH_MAPS = new HashMap<>(); // Integer key represents font style index (0: regular, 1: bold, 2: italic, 3: bold-italic)
    private static final int[] UNICODE_BLOCK_RANGES = {
        0x0020, 0x007E, // Basic Latin
        0x00A0, 0x00FF, // Latin-1 Supplement
        0x2500, 0x257F, // Box Drawing
        0x2580, 0x259F  // Block Elements
    };

    public static final int DEFAULT_PADDING = 0;
    public static final int DEFAULT_ALPHA = 245;
    public static final Range<Integer> LINE_LENGTH = Range.between(1, 128);

    private static final int DEFAULT_PIXEL_SIZE = 2;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;

    static {
        // Precompute character widths for the obfuscation effect
        precomputeCharacterWidths();
    }

    private void drawSolidBorder(Graphics2D graphics, int width, int height, int inset, int stroke) {
        if (stroke <= 0) {
            return;
        }

        int horizontalLength = width - inset * 2;
        int verticalLength = height - inset * 2;
        if (horizontalLength <= 0 || verticalLength <= 0) {
            return;
        }

        // Top
        graphics.fillRect(inset, inset, horizontalLength, stroke);
        // Bottom
        graphics.fillRect(inset, height - inset - stroke, horizontalLength, stroke);
        // Left
        graphics.fillRect(inset, inset + stroke, stroke, verticalLength - stroke * 2);
        // Right
        graphics.fillRect(width - inset - stroke, inset + stroke, stroke, verticalLength - stroke * 2);
    }

    @Getter
    private List<BufferedImage> animationFrames = new ArrayList<>();

    @Getter
    private final List<LineSegment> lines;
    @Getter
    private final int alpha;
    @Getter
    private final int padding;
    private final boolean firstLinePadding;
    @Getter
    private final boolean renderBorder;
    @Getter
    private final boolean centeredText;
    @Getter
    private final int scaleFactor;

    // Scaled values based on scale factor
    private final int pixelSize;
    private final int startXY;
    private final int yIncrement;

    @Getter
    private BufferedImage image;
    @Getter
    private boolean isAnimated = false;
    @Getter
    private int frameDelayMs;
    @Getter
    private int animationFrameCount;

    private transient ChatFormat currentColor;
    private transient Font currentFont;
    private transient int locationX;
    private transient int locationY;
    private transient int largestWidth = 0;
    private transient Map<Integer, Integer> lineMetrics;

    /**
     * Construct a new {@link MinecraftTooltip} instance.
     *
     * @param lines               A list of {@link LineSegment} objects representing the lines of text.
     * @param defaultColor        The default {@link ChatFormat} color to use for the text.
     * @param alpha               The alpha value for the tooltip background. Range: 0-255.
     * @param padding             The padding value for the tooltip. Range: 0-255.
     * @param firstLinePadding    Whether to apply padding to the first line.
     * @param renderBorder        Whether to render a border around the tooltip.
     * @param centeredText        Whether to center the text within the tooltip.
     * @param frameDelayMs        The delay in milliseconds between animation frames.
     * @param animationFrameCount The number of frames to generate for the animation.
     * @param scaleFactor         The scale factor to apply to all pixel sizes.
     */
    private MinecraftTooltip(List<LineSegment> lines, ChatFormat defaultColor, int alpha, int padding, boolean firstLinePadding, boolean renderBorder, boolean centeredText, int frameDelayMs, int animationFrameCount, int scaleFactor) {
        this.lines = lines;
        this.currentColor = defaultColor;
        this.alpha = alpha;
        this.padding = padding;
        this.firstLinePadding = firstLinePadding;
        this.renderBorder = renderBorder;
        this.centeredText = centeredText;
        this.frameDelayMs = frameDelayMs;
        this.animationFrameCount = animationFrameCount;
        this.scaleFactor = scaleFactor;

        this.pixelSize = DEFAULT_PIXEL_SIZE * scaleFactor;
        this.startXY = pixelSize * 5;
        this.yIncrement = pixelSize * 10;

        this.locationX = startXY;
        this.locationY = startXY + pixelSize * 2 + yIncrement / 2;
    }

    /**
     * Precomputes character widths for the text obfuscation/magic formatting effect.
     */
    private static void precomputeCharacterWidths() {
        List<Font> fonts = MinecraftFonts.getAllFonts();

        for (int i = 0; i < fonts.size(); i++) {
            OBFUSCATION_WIDTH_MAPS.put(i, new HashMap<>());
        }

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();
        FontMetrics[] metrics = new FontMetrics[fonts.size()];

        for (int i = 0; i < fonts.size(); i++) {
            metrics[i] = tempG2d.getFontMetrics(fonts.get(i));
        }

        for (int fontIndex = 0; fontIndex < fonts.size(); fontIndex++) {
            Font font = fonts.get(fontIndex);
            FontMetrics fontMetrics = metrics[fontIndex];

            Map<Integer, List<Character>> map = OBFUSCATION_WIDTH_MAPS.get(fontIndex);

            for (int range = 0; range < UNICODE_BLOCK_RANGES.length; range += 2) {
                for (int codePoint = UNICODE_BLOCK_RANGES[range]; codePoint <= UNICODE_BLOCK_RANGES[range + 1]; codePoint++) {
                    char c = (char) codePoint;

                    if (MinecraftFonts.canRender(font, c)) {
                        int width = fontMetrics.charWidth(c);
                        if (width > 0) {
                            map.computeIfAbsent(width, k -> new ArrayList<>()).add(c);
                        }
                    }
                }
            }
        }

        tempG2d.dispose();

        log.info("Precomputed obfuscation character widths. Regular: {} chars, Bold: {} chars, Italic: {} chars, BoldItalic: {} chars.",
            OBFUSCATION_WIDTH_MAPS.get(0).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(1).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(2).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(3).values().stream().mapToInt(List::size).sum()
        );
    }

    /**
     * Creates a new {@link MinecraftTooltip} instance.
     *
     * @return A new {@link Builder} instance for creating a {@link MinecraftTooltip}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether first line padding is enabled.
     *
     * @return true if first line padding is enabled
     */
    public boolean hasFirstLinePadding() {
        return this.firstLinePadding;
    }

    /**
     * Adds padding to the tooltip frame.
     *
     * @param frame The {@link BufferedImage} frame to add padding to.
     *
     * @return The padded {@link BufferedImage} frame.
     */
    private BufferedImage addPadding(BufferedImage frame) {
        if (this.getPadding() <= 0) {
            return frame;
        }

        BufferedImage paddedFrame = new BufferedImage(
            frame.getWidth() + this.getPadding() * 2,
            frame.getHeight() + this.getPadding() * 2,
            BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D graphics2D = paddedFrame.createGraphics();
        graphics2D.drawImage(frame, this.getPadding(), this.getPadding(), frame.getWidth(), frame.getHeight(), null);
        graphics2D.dispose();

        return paddedFrame;
    }

    /**
     * Draws the borders around the tooltip.
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     * @param width         The width of the tooltip.
     * @param height        The height of the tooltip.
     */
    private void drawBorders(Graphics2D frameGraphics, int width, int height) {
        // Draw Darker Purple Border
        frameGraphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
        frameGraphics.fillRect(0, pixelSize, pixelSize, height - pixelSize * 2); // Left
        frameGraphics.fillRect(pixelSize, 0, width - pixelSize * 2, pixelSize); // Top
        frameGraphics.fillRect(width - pixelSize, pixelSize, pixelSize, height - pixelSize * 2); // Right
        frameGraphics.fillRect(pixelSize, height - pixelSize, width - pixelSize * 2, pixelSize); // Bottom

        // Draw Purple Border
        frameGraphics.setColor(new Color(37, 0, 94, this.isAnimated ? 255 : this.getAlpha()));

        int outerInset = pixelSize;
        int outerThickness = Math.max(1, pixelSize / 2);
        drawBorderWithThickness(frameGraphics, width, height, outerInset, outerThickness);

        int gapBetweenBorders = 0;
        int innerInset = outerInset + outerThickness + gapBetweenBorders;
        int innerThickness = Math.max(1, (int) Math.round(pixelSize / 2.0));
        if (innerInset * 2 < width && innerInset * 2 < height) {
            drawBorderWithThickness(frameGraphics, width, height, innerInset, innerThickness);
        }
    }

    private void drawBorderWithThickness(Graphics2D graphics, int width, int height, int inset, int thickness) {
        if (thickness <= 0) {
            return;
        }

        int innerWidth = width - inset * 2;
        int innerHeight = height - inset * 2;
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }

        // Top edge
        graphics.fillRect(inset, inset, innerWidth, thickness);
        // Bottom edge
        graphics.fillRect(inset, height - inset - thickness, innerWidth, thickness);

        int verticalHeight = innerHeight - thickness * 2;
        if (verticalHeight <= 0) {
            return;
        }

        // Left edge
        graphics.fillRect(inset, inset + thickness, thickness, verticalHeight);
        // Right edge
        graphics.fillRect(width - inset - thickness, inset + thickness, thickness, verticalHeight);
    }

    /**
     * Calculates the width of a line segment.
     *
     * @param graphics The {@link Graphics2D} object to measure text on.
     * @param line     The {@link LineSegment} to measure.
     *
     * @return The width of the line segment.
     */
    private int calculateLineWidth(Graphics2D graphics, LineSegment line) {
        int lineWidth = 0;
        for (ColorSegment segment : line.getSegments()) {
            Font baseFont = MinecraftFonts.getFont(segment.isBold(), segment.isItalic());
            Font font = scaleFactor > 1 ? baseFont.deriveFont(baseFont.getSize2D() * scaleFactor) : baseFont;
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics(font);
            String segmentText = segment.getText();

            for (int i = 0; i < segmentText.length(); ) {
                int codePoint = segmentText.codePointAt(i);

                // Skip variation selectors (U+FE0E and U+FE0F) - they're zero-width control characters
                if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                    i += Character.charCount(codePoint);
                    continue;
                }

                String charStr = new String(Character.toChars(codePoint));

                if (font.canDisplayUpTo(charStr) == -1) {
                    lineWidth += metrics.stringWidth(charStr);
                } else {
                    Font fallbackFont = MinecraftFonts.getFallbackFont(codePoint, font.getSize2D());
                    if (fallbackFont != null) {
                        graphics.setFont(fallbackFont);
                        FontMetrics fallbackMetrics = graphics.getFontMetrics(fallbackFont);
                        lineWidth += fallbackMetrics.stringWidth(charStr);
                        graphics.setFont(font);
                    } else {
                        lineWidth += metrics.stringWidth(charStr);
                    }
                }
                i += Character.charCount(codePoint);
            }
        }

        return lineWidth;
    }

    /**
     * Calculates the largest width of all lines in a tooltip and sets the {@link #largestWidth} field.
     *
     * @param measureGraphics The {@link Graphics2D} object to measure text on.
     */
    private void measureLines(Graphics2D measureGraphics) {
        this.lineMetrics = new HashMap<>();
        this.locationY = startXY + pixelSize * 2 + yIncrement / 2;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = calculateLineWidth(measureGraphics, line);
            this.lineMetrics.put(lineIndex, lineWidth);

            int extraPadding = (lineIndex == 0 && this.hasFirstLinePadding()) ? pixelSize * 2 : 0;
            this.locationY += yIncrement + extraPadding;
        }

        this.largestWidth = this.lineMetrics.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * Draws lines of text on the image.
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     */
    private void drawLinesInternal(Graphics2D frameGraphics) {
        this.locationY = startXY + pixelSize * 2 + yIncrement / 2;
        this.isAnimated = false;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = this.lineMetrics.getOrDefault(lineIndex, 0);

            // Adjust X position based on if text is centered
            if (this.centeredText) {
                this.locationX = startXY + (this.largestWidth - lineWidth) / 2;
            } else {
                this.locationX = startXY;
            }

            // Draw segments for the line
            for (ColorSegment segment : line.getSegments()) {
                if (segment.isObfuscated()) {
                    this.isAnimated = true;
                }
                this.drawString(frameGraphics, segment);
            }

            // Increment Y position for the next line
            int extraPadding = (lineIndex == 0 && this.hasFirstLinePadding()) ? pixelSize * 2 : 0;
            this.locationY += yIncrement + extraPadding;
        }
    }

    /**
     * Draws a string with the specified formatting.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param colorSegment The {@link ColorSegment} containing formatted text.
     */
    private void drawString(Graphics2D graphics, @NotNull ColorSegment colorSegment) {
        Font baseFont = MinecraftFonts.getFont(colorSegment.isBold(), colorSegment.isItalic());
        this.currentFont = scaleFactor > 1 ? baseFont.deriveFont(baseFont.getSize2D() * scaleFactor) : baseFont;
        this.currentColor = colorSegment.getColor().orElse(ChatFormat.GRAY);
        graphics.setFont(this.currentFont);
        FontMetrics metrics = graphics.getFontMetrics(this.currentFont);

        String text = colorSegment.getText();
        log.debug("Drawing text segment '{}' with font: {} (bold: {}, italic: {})",
            text, this.currentFont.getName(), colorSegment.isBold(), colorSegment.isItalic());
        StringBuilder subWord = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);

            // Skip variation selectors (U+FE0E and U+FE0F) - they're zero-width control characters
            if (codePoint == 0xFE0E || codePoint == 0xFE0F) {
                i += Character.charCount(codePoint);
                continue;
            }

            String charStr = new String(Character.toChars(codePoint));
            int charCount = Character.charCount(codePoint);

            if (colorSegment.isObfuscated()) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw obfuscated character
                if (codePoint <= 0xFFFF) {
                    drawObfuscatedChar(graphics, (char) codePoint, colorSegment, metrics);
                } else {
                    drawSymbolAndAdvance(graphics, codePoint, charStr, colorSegment);
                }

                i += charCount;
                continue;
            }

            if (this.currentFont.canDisplayUpTo(charStr) != -1) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw symbol using unicode fallback font
                drawSymbolAndAdvance(graphics, codePoint, charStr, colorSegment);
                i += charCount;
                continue;
            }

            subWord.append(charStr);
            i += charCount;
        }

        // Draw any remaining subWord, if any
        if (!subWord.isEmpty()) {
            drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
        }
    }

    /**
     * Draw a sub-word with text effects.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param subWord      The sub-word to draw.
     * @param colorSegment The {@link ColorSegment} containing the color and style information.
     * @param metrics      The {@link FontMetrics} object to measure the character width.
     */
    private void drawSubWord(Graphics2D graphics, String subWord, ColorSegment colorSegment, FontMetrics metrics) {
        if (subWord.isEmpty()) {
            return;
        }

        int width = metrics.stringWidth(subWord);
        drawTextWithEffects(graphics, subWord, colorSegment, width);
        this.locationX += width;
    }

    /**
     * Draws a symbol using a fallback font when the Minecraft font cannot render it.
     *
     * @param graphics  The {@link Graphics2D} object to draw on.
     * @param codePoint The Unicode code point of the character.
     * @param charStr   The character as a string (handles surrogate pairs).
     * @param segment   The color segment containing style information.
     */
    private void drawSymbolAndAdvance(Graphics2D graphics, int codePoint, String charStr, ColorSegment segment) {
        log.warn("Character '{}' (U+{}) cannot be displayed by font '{}'",
            charStr, String.format("%04X", codePoint), this.currentFont.getName());

        Font fallbackFont = MinecraftFonts.getFallbackFont(codePoint, this.currentFont.getSize2D());
        Font fontToUse = fallbackFont != null ? fallbackFont : this.currentFont;

        if (fallbackFont != null) {
            log.debug("Switching font: '{}' -> '{}'", this.currentFont.getName(), fallbackFont.getName());
        }

        graphics.setFont(fontToUse);
        FontMetrics symbolMetrics = graphics.getFontMetrics(fontToUse);
        int width = symbolMetrics.stringWidth(charStr);

        drawTextWithEffects(graphics, charStr, segment, width);

        this.locationX += width;
        graphics.setFont(this.currentFont);

        if (fallbackFont != null) {
            log.debug("Switching font: '{}' -> '{}'", fallbackFont.getName(), this.currentFont.getName());
        }
    }

    /**
     * Draw an obfuscated character with a random character of the same width.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param originalChar The original character to obfuscate.
     * @param colorSegment The {@link ColorSegment} containing the color and style information.
     * @param metrics      The {@link FontMetrics} object to measure the character width.
     */
    private void drawObfuscatedChar(Graphics2D graphics, char originalChar, ColorSegment colorSegment, FontMetrics metrics) {
        int originalWidth = metrics.charWidth(originalChar);
        String charToDrawStr = String.valueOf(originalChar); // Default fallback

        int fontStyleIndex = (colorSegment.isBold() ? 1 : 0) + (colorSegment.isItalic() ? 2 : 0);
        Map<Integer, List<Character>> widthMap = OBFUSCATION_WIDTH_MAPS.get(fontStyleIndex);
        List<Character> matchingWidthChars = (widthMap != null) ? widthMap.get(originalWidth) : null;

        if (matchingWidthChars != null && !matchingWidthChars.isEmpty()) {
            char randomChar = matchingWidthChars.get(ThreadLocalRandom.current().nextInt(matchingWidthChars.size()));
            charToDrawStr = String.valueOf(randomChar);
            log.trace("Obfuscating character '{}' (U+{}) with '{}' (U+{}) using font: {}",
                originalChar, String.format("%04X", (int) originalChar),
                randomChar, String.format("%04X", (int) randomChar),
                this.currentFont.getName());
        } else {
            log.warn("No matching character found with width {} for original character '{}' (U+{}), using original",
                originalWidth, originalChar, String.format("%04X", (int) originalChar));
        }

        // Recalculate width for the potentially different character
        int drawnWidth = metrics.stringWidth(charToDrawStr);
        drawTextWithEffects(graphics, charToDrawStr, colorSegment, drawnWidth);
        this.locationX += drawnWidth;
    }

    /**
     * Draws the text with strikethrough, underline and drop shadow effects
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     * @param textToDraw    The text to draw.
     * @param colorSegment  The {@link ColorSegment} containing the color and style information.
     * @param width         The width of the text to draw.
     */
    private void drawTextWithEffects(Graphics2D frameGraphics, String textToDraw, ColorSegment colorSegment, int width) {
        // Draw Strikethrough Drop Shadow
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET * scaleFactor, true);
        }

        // Draw Underlined Drop Shadow
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX - pixelSize, this.locationY, 1, UNDERLINE_OFFSET * scaleFactor, true);
        }

        // Draw Drop Shadow Text
        frameGraphics.setColor(this.currentColor.getBackgroundColor());
        frameGraphics.drawString(textToDraw, this.locationX + pixelSize, this.locationY + pixelSize);

        // Draw Text
        frameGraphics.setColor(this.currentColor.getColor());
        frameGraphics.drawString(textToDraw, this.locationX, this.locationY);

        // Draw Strikethrough
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET * scaleFactor, false);
        }

        // Draw Underlined
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX - pixelSize, this.locationY, 1, UNDERLINE_OFFSET * scaleFactor, false);
        }
    }

    /**
     * Draws a thick line on the image with optional drop shadow.
     */
    private void drawThickLineInternal(Graphics2D frameGraphics, int width, int xPosition, int yPosition, int xOffset, int yOffset, boolean dropShadow) {
        int xPosition1 = xPosition;
        int xPosition2 = xPosition + width + xOffset;
        yPosition += yOffset;

        if (dropShadow) {
            xPosition1 += pixelSize;
            xPosition2 += pixelSize;
            yPosition += pixelSize;
        }

        frameGraphics.setColor(dropShadow ? this.currentColor.getBackgroundColor() : this.currentColor.getColor());
        frameGraphics.drawLine(xPosition1, yPosition, xPosition2, yPosition);
        frameGraphics.drawLine(xPosition1, yPosition + 1, xPosition2, yPosition + 1);
    }

    /**
     * Draws all tooltip frames.
     */
    public MinecraftTooltip render() {
        this.animationFrames.clear();

        // Determine the largest width using the measureLines method
        BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = dummyImage.createGraphics();
        measureLines(measureGraphics);
        int measuredHeight = this.locationY;
        measureGraphics.dispose();

        // Calculate final dimensions based on the measured largestWidth and height
        int finalWidth = startXY + this.largestWidth + startXY;
        int finalHeight = measuredHeight - (yIncrement + (this.lines.isEmpty() || !this.firstLinePadding ? 0 : pixelSize * 2)) + startXY + pixelSize * 2;

        // Determine if we need to animate the image beforehand
        this.isAnimated = this.lines.stream()
            .flatMap(line -> line.getSegments().stream())
            .anyMatch(ColorSegment::isObfuscated);

        int framesToGenerate = this.isAnimated ? this.animationFrameCount : 1;

        for (int i = 0; i < framesToGenerate; i++) {
            // Use the final calculated dimensions for the frame
            int frameWidth = Math.max(1, finalWidth);
            int frameHeight = Math.max(1, finalHeight);
            BufferedImage frameImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();

            // Draw background first
            graphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
            graphics.fillRect(
                pixelSize * 2, // Inner edge of border
                pixelSize * 2,
                frameWidth - pixelSize * 4, // Width inside borders
                frameHeight - pixelSize * 4 // Height inside borders
            );

            drawLinesInternal(graphics);

            // Draw borders onto the frame
            if (this.renderBorder) {
                this.drawBorders(graphics, frameWidth, frameHeight);
            }

            // Add padding after rendering tooltip content and borders
            BufferedImage processedFrame = this.addPadding(frameImage);
            graphics.dispose();

            this.animationFrames.add(processedFrame);
        }

        // Set the static image to the first frame if there are any
        if (!this.animationFrames.isEmpty()) {
            this.image = this.animationFrames.get(0);
        }

        return this;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder implements ClassBuilder<MinecraftTooltip> {
        @Getter
        private final List<LineSegment> lines = new ArrayList<>();
        private ChatFormat defaultColor = ChatFormat.GRAY;
        private int alpha = DEFAULT_ALPHA;
        private int padding = 0;
        private boolean firstLinePadding = true;
        private boolean renderBorder = true;
        private boolean centeredText = false;
        private int frameDelayMs = 50;
        private int animationFrameCount = 10;
        private int scaleFactor = 1;

        public Builder hasFirstLinePadding() {
            return this.hasFirstLinePadding(true);
        }

        public Builder hasFirstLinePadding(boolean value) {
            this.firstLinePadding = value;
            return this;
        }

        public Builder setRenderBorder(boolean renderBorder) {
            this.renderBorder = renderBorder;
            return this;
        }

        public Builder isTextCentered(boolean value) {
            this.centeredText = value;
            return this;
        }

        public Builder withAlpha(int value) {
            // If renderBorder, force alpha to 255 so it shows up
            if (this.renderBorder) {
                this.alpha = 255;
                return this;
            }

            this.alpha = Range.between(0, 255).fit(value);
            return this;
        }

        public Builder withDefaultColor(@NotNull ChatFormat chatColor) {
            this.defaultColor = chatColor;
            return this;
        }

        public Builder withLines(@NotNull LineSegment... lines) {
            return this.withLines(Arrays.asList(lines));
        }

        public Builder withLines(@NotNull Iterable<LineSegment> lines) {
            lines.forEach(this.lines::add);
            return this;
        }

        public Builder withPadding(int padding) {
            this.padding = Math.max(0, padding);
            return this;
        }

        public Builder withSegments(@NotNull ColorSegment... segments) {
            return this.withSegments(Arrays.asList(segments));
        }

        public Builder withSegments(@NotNull Iterable<ColorSegment> segments) {
            this.lines.add(LineSegment.builder().withSegments(segments).build());
            return this;
        }

        public Builder withFrameDelayMs(int delay) {
            this.frameDelayMs = Math.max(10, delay);
            return this;
        }

        public Builder withAnimationFrameCount(int count) {
            this.animationFrameCount = Math.max(1, count);
            return this;
        }

        public Builder withScaleFactor(int scaleFactor) {
            this.scaleFactor = Math.max(1, scaleFactor);
            return this;
        }

        @Override
        public @NotNull MinecraftTooltip build() {
            return new MinecraftTooltip(
                this.lines,
                this.defaultColor,
                this.alpha,
                this.padding,
                this.firstLinePadding,
                this.renderBorder,
                this.centeredText,
                this.frameDelayMs,
                this.animationFrameCount,
                this.scaleFactor
            );
        }
    }
}