package net.hypixel.nerdbot.generator.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.ColorSegment;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.util.FontUtils;
import net.hypixel.nerdbot.util.Range;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
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

    private static final int PIXEL_SIZE = 2;
    private static final int START_XY = PIXEL_SIZE * 5;
    private static final int Y_INCREMENT = PIXEL_SIZE * 10;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;
    private static final @NotNull List<Font> MINECRAFT_FONTS = new ArrayList<>();
    private static final Font SANS_SERIF_FONT;

    static {
        SANS_SERIF_FONT = new Font("SansSerif", Font.PLAIN, 20);

        MINECRAFT_FONTS.addAll(
            Arrays.asList(
                FontUtils.initFont("/minecraft/fonts/minecraft.otf", 15.5f),
                FontUtils.initFont("/minecraft/fonts/3_Minecraft-Bold.otf", 20.0f),
                FontUtils.initFont("/minecraft/fonts/2_Minecraft-Italic.otf", 20.5f),
                FontUtils.initFont("/minecraft/fonts/4_Minecraft-BoldItalic.otf", 20.5f)
            )
        );

        // Register Minecraft Fonts
        MINECRAFT_FONTS.forEach(GraphicsEnvironment.getLocalGraphicsEnvironment()::registerFont);
        // Precompute character widths for the obfuscation effect
        precomputeCharacterWidths();
    }

    @Getter
    private List<BufferedImage> animationFrames = new ArrayList<>();

    @Getter
    private final List<LineSegment> lines;
    @Getter
    private final int alpha;
    @Getter
    private final int padding;
    @Getter
    private final boolean paddingFirstLine;
    @Getter
    private final boolean renderBorder;
    @Getter
    private final boolean centeredText;
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
    private transient int locationX = START_XY;
    private transient int locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
    private transient int largestWidth = 0;
    private transient Map<Integer, Integer> lineMetrics;

    /**
     * Construct a new {@link MinecraftTooltip} instance.
     *
     * @param lines               A list of {@link LineSegment} objects representing the lines of text.
     * @param defaultColor        The default {@link ChatFormat} color to use for the text.
     * @param alpha               The alpha value for the tooltip background. Range: 0-255.
     * @param padding             The padding value for the tooltip. Range: 0-255.
     * @param paddingFirstLine    Whether to apply padding to the first line.
     * @param renderBorder        Whether to render a border around the tooltip.
     * @param centeredText        Whether to center the text within the tooltip.
     * @param frameDelayMs        The delay in milliseconds between animation frames.
     * @param animationFrameCount The number of frames to generate for the animation.
     */
    private MinecraftTooltip(List<LineSegment> lines, ChatFormat defaultColor, int alpha, int padding, boolean paddingFirstLine, boolean renderBorder, boolean centeredText, int frameDelayMs, int animationFrameCount) {
        this.lines = lines;
        this.currentColor = defaultColor;
        this.alpha = alpha;
        this.padding = padding;
        this.paddingFirstLine = paddingFirstLine;
        this.renderBorder = renderBorder;
        this.centeredText = centeredText;
        this.frameDelayMs = frameDelayMs;
        this.animationFrameCount = animationFrameCount;
    }

    /**
     * Precomputes character widths for the text obfuscation/magic formatting effect.
     */
    private static void precomputeCharacterWidths() {
        for (int i = 0; i < MINECRAFT_FONTS.size(); i++) {
            OBFUSCATION_WIDTH_MAPS.put(i, new HashMap<>());
        }

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();
        FontMetrics[] metrics = new FontMetrics[MINECRAFT_FONTS.size()];

        for (int i = 0; i < MINECRAFT_FONTS.size(); i++) {
            Font font = MINECRAFT_FONTS.get(i);

            if (font == null) {
                log.error("Minecraft font at index {} is null, so we can't precompute the character widths", i);
                continue;
            }

            metrics[i] = tempG2d.getFontMetrics(font);
        }

        for (int fontIndex = 0; fontIndex < MINECRAFT_FONTS.size(); fontIndex++) {
            Font font = MINECRAFT_FONTS.get(fontIndex);
            FontMetrics fontMetrics = metrics[fontIndex];

            if (font == null || fontMetrics == null) {
                continue;
            }

            Map<Integer, List<Character>> map = OBFUSCATION_WIDTH_MAPS.get(fontIndex);

            for (int range = 0; range < UNICODE_BLOCK_RANGES.length; range += 2) {
                for (int codePoint = UNICODE_BLOCK_RANGES[range]; codePoint <= UNICODE_BLOCK_RANGES[range + 1]; codePoint++) {
                    char c = (char) codePoint;

                    if (font.canDisplay(c)) {
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
        frameGraphics.fillRect(0, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2); // Left
        frameGraphics.fillRect(PIXEL_SIZE, 0, width - PIXEL_SIZE * 2, PIXEL_SIZE); // Top
        frameGraphics.fillRect(width - PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2); // Right
        frameGraphics.fillRect(PIXEL_SIZE, height - PIXEL_SIZE, width - PIXEL_SIZE * 2, PIXEL_SIZE); // Bottom

        // Draw Purple Border
        frameGraphics.setColor(new Color(37, 0, 94, this.isAnimated ? 255 : this.getAlpha()));
        frameGraphics.drawRect(PIXEL_SIZE, PIXEL_SIZE, width - PIXEL_SIZE * 2 - 1, height - PIXEL_SIZE * 2 - 1);
        frameGraphics.drawRect(PIXEL_SIZE + 1, PIXEL_SIZE + 1, width - PIXEL_SIZE * 3 - 1, height - PIXEL_SIZE * 3 - 1);
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
            Font font = MINECRAFT_FONTS.get((segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0));
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics(font);
            String segmentText = segment.getText();

            for (int charIndex = 0; charIndex < segmentText.length(); charIndex++) {
                char character = segmentText.charAt(charIndex);

                if (font.canDisplay(character)) {
                    lineWidth += metrics.charWidth(character);
                } else {
                    Font symbolFont = SANS_SERIF_FONT;
                    graphics.setFont(symbolFont);
                    FontMetrics symbolMetrics = graphics.getFontMetrics(symbolFont);
                    lineWidth += symbolMetrics.charWidth(character);
                    graphics.setFont(font);
                }
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
        this.locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = calculateLineWidth(measureGraphics, line);
            this.lineMetrics.put(lineIndex, lineWidth);

            this.locationY += Y_INCREMENT + (lineIndex == 0 && this.isPaddingFirstLine() ? PIXEL_SIZE * 2 : 0);
        }

        this.largestWidth = this.lineMetrics.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /**
     * Draws lines of text on the image.
     *
     * @param frameGraphics The {@link Graphics2D} object to draw on.
     */
    private void drawLinesInternal(Graphics2D frameGraphics) {
        this.locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
        this.isAnimated = false;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            int lineWidth = this.lineMetrics.getOrDefault(lineIndex, 0);

            // Adjust X position based on if text is centered
            if (this.centeredText) {
                this.locationX = START_XY + (this.largestWidth - lineWidth) / 2;
            } else {
                this.locationX = START_XY;
            }

            // Draw segments for the line
            for (ColorSegment segment : line.getSegments()) {
                if (segment.isObfuscated()) {
                    this.isAnimated = true;
                }
                this.drawString(frameGraphics, segment);
            }

            // Increment Y position for the next line
            this.locationY += Y_INCREMENT + (lineIndex == 0 && this.isPaddingFirstLine() ? PIXEL_SIZE * 2 : 0);
        }
    }

    /**
     * Draws a string with the specified formatting.
     *
     * @param graphics     The {@link Graphics2D} object to draw on.
     * @param colorSegment The {@link ColorSegment} containing formatted text.
     */
    private void drawString(Graphics2D graphics, @NotNull ColorSegment colorSegment) {
        this.currentFont = MINECRAFT_FONTS.get((colorSegment.isBold() ? 1 : 0) + (colorSegment.isItalic() ? 2 : 0));
        this.currentColor = colorSegment.getColor().orElse(ChatFormat.GRAY);
        graphics.setFont(this.currentFont);
        FontMetrics metrics = graphics.getFontMetrics(this.currentFont);

        String text = colorSegment.getText();
        StringBuilder subWord = new StringBuilder();

        for (int charIndex = 0; charIndex < text.length(); charIndex++) {
            char character = text.charAt(charIndex);

            if (colorSegment.isObfuscated()) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw obfuscated character
                drawObfuscatedChar(graphics, character, colorSegment, metrics);
                continue;
            }

            if (!this.currentFont.canDisplay(character)) {
                // Draw previous subWord, if any
                if (!subWord.isEmpty()) {
                    drawSubWord(graphics, subWord.toString(), colorSegment, metrics);
                    subWord.setLength(0);
                }

                // Draw symbol using SANS_SERIF_FONT
                drawSymbolAndAdvance(graphics, character, colorSegment);
                continue;
            }

            subWord.append(character);
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
     * Draws a symbol using the {@link MinecraftTooltip#SANS_SERIF_FONT} font.
     *
     * @param graphics The {@link Graphics2D} object to draw on.
     * @param symbol   The symbol to draw.
     */
    private void drawSymbolAndAdvance(Graphics2D graphics, char symbol, ColorSegment segment) {
        graphics.setFont(SANS_SERIF_FONT);
        FontMetrics symbolMetrics = graphics.getFontMetrics(SANS_SERIF_FONT);
        String symbolStr = Character.toString(symbol);
        int width = symbolMetrics.stringWidth(symbolStr);

        drawTextWithEffects(graphics, symbolStr, segment, width);

        this.locationX += width;
        graphics.setFont(this.currentFont);
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
            charToDrawStr = String.valueOf(matchingWidthChars.get(ThreadLocalRandom.current().nextInt(matchingWidthChars.size())));
        } else {
            log.warn("No matching character found with width {} for original character '{}', using original", originalWidth, originalChar);
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
            this.drawThickLineInternal(frameGraphics, width, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, true);
        }

        // Draw Underlined Drop Shadow
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, true);
        }

        // Draw Drop Shadow Text
        frameGraphics.setColor(this.currentColor.getBackgroundColor());
        frameGraphics.drawString(textToDraw, this.locationX + PIXEL_SIZE, this.locationY + PIXEL_SIZE);

        // Draw Text
        frameGraphics.setColor(this.currentColor.getColor());
        frameGraphics.drawString(textToDraw, this.locationX, this.locationY);

        // Draw Strikethrough
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, false);
        }

        // Draw Underlined
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, width, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, false);
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
            xPosition1 += PIXEL_SIZE;
            xPosition2 += PIXEL_SIZE;
            yPosition += PIXEL_SIZE;
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
        int finalWidth = START_XY + this.largestWidth + START_XY;
        int finalHeight = measuredHeight - (Y_INCREMENT + (this.lines.isEmpty() || !this.paddingFirstLine ? 0 : PIXEL_SIZE * 2)) + START_XY + PIXEL_SIZE * 2;

        // Determine if we need to animate the image beforehand
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        this.isAnimated = false;
        drawLinesInternal(tempGraphics);
        tempGraphics.dispose();

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
                PIXEL_SIZE * 2, // Inner edge of border
                PIXEL_SIZE * 2,
                frameWidth - PIXEL_SIZE * 4, // Width inside borders
                frameHeight - PIXEL_SIZE * 4 // Height inside borders
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
        private boolean paddingFirstLine = true;
        private boolean renderBorder = true;
        private boolean centeredText = false;
        private int frameDelayMs = 50;
        private int animationFrameCount = 10;

        public Builder isPaddingFirstLine() {
            return this.isPaddingFirstLine(true);
        }

        public Builder isPaddingFirstLine(boolean value) {
            this.paddingFirstLine = value;
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

        public Builder withRarityLineBreak() {
            return this.withSegments(ColorSegment.builder().build());
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

        @Override
        public @NotNull MinecraftTooltip build() {
            return new MinecraftTooltip(
                this.lines,
                this.defaultColor,
                this.alpha,
                this.padding,
                this.paddingFirstLine,
                this.renderBorder,
                this.centeredText,
                this.frameDelayMs,
                this.animationFrameCount
            );
        }
    }
}