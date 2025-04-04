package net.hypixel.nerdbot.generator.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.ColorSegment;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.util.Range;
import net.hypixel.nerdbot.util.Util;
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

@Log4j2
public class MinecraftTooltip {

    private static final Map<Boolean, Map<Integer, List<Character>>> OBFUSCATION_WIDTH_MAPS = new HashMap<>(); // Boolean to indicate bold or regular
    private static final int[] UNICODE_BLOCK_RANGES = {
        0x0020, 0x007E, // Basic Latin
        0x00A0, 0x00FF, // Latin-1 Supplement
        0x2500, 0x257F, // Box Drawing
        0x2580, 0x259F  // Block Elements
    };

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
                Util.initFont("/minecraft/fonts/minecraft.otf", 15.5f),
                Util.initFont("/minecraft/fonts/3_Minecraft-Bold.otf", 20.0f),
                Util.initFont("/minecraft/fonts/2_Minecraft-Italic.otf", 20.5f),
                Util.initFont("/minecraft/fonts/4_Minecraft-BoldItalic.otf", 20.5f)
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
    private BufferedImage image;
    @Getter
    private boolean isAnimated = false;
    @Getter
    private int frameDelayMs;
    @Getter
    private int animationFrameCount;

    private MinecraftTooltip(List<LineSegment> lines, ChatFormat defaultColor, int alpha, int padding, boolean paddingFirstLine, boolean renderBorder, int frameDelayMs, int animationFrameCount) {
        this.alpha = alpha;
        this.padding = padding;
        this.paddingFirstLine = paddingFirstLine;
        this.lines = lines;
        this.currentColor = defaultColor;
        this.renderBorder = renderBorder;
        this.frameDelayMs = frameDelayMs;
        this.animationFrameCount = animationFrameCount;
    }

    @Getter
    private ChatFormat currentColor;
    private Font currentFont;

    private int locationX = START_XY;
    private int locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
    private int largestWidth = 0;

    private static void precomputeCharacterWidths() {
        OBFUSCATION_WIDTH_MAPS.put(false, new HashMap<>()); // Map for default style
        OBFUSCATION_WIDTH_MAPS.put(true, new HashMap<>());  // Map for bold style

        Font defaultFont = MINECRAFT_FONTS.get(0);
        Font boldFont = MINECRAFT_FONTS.get(1);

        if (defaultFont == null || boldFont == null) {
            log.error("Default or Bold Minecraft font not initialized, cannot precompute character widths.");
            return;
        }

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImg.createGraphics();
        FontMetrics defaultMetrics = tempG2d.getFontMetrics(defaultFont);
        FontMetrics boldMetrics = tempG2d.getFontMetrics(boldFont);

        for (int range = 0; range < UNICODE_BLOCK_RANGES.length; range += 2) {
            for (int codePoint = UNICODE_BLOCK_RANGES[range]; codePoint <= UNICODE_BLOCK_RANGES[range + 1]; codePoint++) {
                char c = (char) codePoint;

                if (defaultFont.canDisplay(c)) {
                    int width = defaultMetrics.charWidth(c);
                    if (width > 0) {
                        OBFUSCATION_WIDTH_MAPS.get(false).computeIfAbsent(width, k -> new ArrayList<>()).add(c);
                    }
                }

                if (boldFont.canDisplay(c)) {
                    int width = boldMetrics.charWidth(c);
                    if (width > 0) {
                        OBFUSCATION_WIDTH_MAPS.get(true).computeIfAbsent(width, k -> new ArrayList<>()).add(c);
                    }
                }
            }
        }
        tempG2d.dispose();
        log.info("Precomputed obfuscation character widths. Default: {} chars, Bold: {} chars.",
            OBFUSCATION_WIDTH_MAPS.get(false).values().stream().mapToInt(List::size).sum(),
            OBFUSCATION_WIDTH_MAPS.get(true).values().stream().mapToInt(List::size).sum()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    private BufferedImage cropFrame(BufferedImage frame, int finalWidth, int finalHeight) {
        int cropWidth = Math.min(finalWidth + START_XY, frame.getWidth());
        int cropHeight = Math.min(finalHeight, frame.getHeight());

        if (cropWidth <= 0 || cropHeight <= 0) {
            log.warn("Attempted to crop frame with invalid dimensions: width={}, height={}", cropWidth, cropHeight);
            return frame.getWidth() > 0 && frame.getHeight() > 0 ? frame : new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        return frame.getSubimage(0, 0, cropWidth, cropHeight);
    }

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

    private void drawBorders(Graphics2D frameGraphics, int width, int height) {
        // Draw Darker Purple Border
        frameGraphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
        frameGraphics.fillRect(0, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        frameGraphics.fillRect(PIXEL_SIZE, 0, width - PIXEL_SIZE * 2, PIXEL_SIZE);
        frameGraphics.fillRect(width - PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        frameGraphics.fillRect(PIXEL_SIZE, height - PIXEL_SIZE, width - PIXEL_SIZE * 2, PIXEL_SIZE);

        // Draw Purple Border
        frameGraphics.setColor(new Color(37, 0, 94, this.isAnimated ? 255 : this.getAlpha()));
        frameGraphics.drawRect(PIXEL_SIZE, PIXEL_SIZE, width - PIXEL_SIZE * 2 - 1, height - PIXEL_SIZE * 2 - 1);
        frameGraphics.drawRect(PIXEL_SIZE + 1, PIXEL_SIZE + 1, width - PIXEL_SIZE * 3 - 1, height - PIXEL_SIZE * 3 - 1);
    }

    private void drawLinesInternal(Graphics2D frameGraphics) {
        this.locationX = START_XY;
        this.locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
        int currentFrameLargestWidth = 0;

        for (int lineIndex = 0; lineIndex < this.getLines().size(); lineIndex++) {
            LineSegment line = this.getLines().get(lineIndex);
            for (ColorSegment segment : line.getSegments()) {
                if (segment.isObfuscated()) {
                    this.isAnimated = true;
                }

                this.currentFont = MINECRAFT_FONTS.get((segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0));
                frameGraphics.setFont(this.currentFont);
                this.currentColor = segment.getColor().orElse(ChatFormat.GRAY);

                StringBuilder subWord = new StringBuilder();
                String segmentText = segment.getText();

                for (int charIndex = 0; charIndex < segmentText.length(); charIndex++) {
                    char character = segmentText.charAt(charIndex);

                    if (segment.isObfuscated()) {
                        if (!subWord.isEmpty()) {
                            this.drawStringInternal(frameGraphics, subWord.toString(), segment, -1);
                            subWord.setLength(0);
                        }

                        this.drawStringInternal(frameGraphics, String.valueOf(character), segment, charIndex);
                        continue;
                    }

                    if (!this.currentFont.canDisplay(character)) {
                        this.drawStringInternal(frameGraphics, subWord.toString(), segment, -1);
                        subWord.setLength(0);
                        this.drawSymbolInternal(frameGraphics, character, segment);
                        continue;
                    }

                    subWord.append(character);
                }

                this.drawStringInternal(frameGraphics, subWord.toString(), segment, -1);
            }

            this.locationY += Y_INCREMENT + (lineIndex == 0 && this.isPaddingFirstLine() ? PIXEL_SIZE * 2 : 0);
            currentFrameLargestWidth = Math.max(this.locationX, currentFrameLargestWidth);
            this.locationX = START_XY;
        }

        this.largestWidth = Math.max(this.largestWidth, currentFrameLargestWidth);
    }

    /**
     * Draws a symbol on the image, and updates the pointer location.
     *
     * @param symbol The symbol to draw.
     */
    private void drawSymbolInternal(Graphics2D frameGraphics, char symbol, @NotNull ColorSegment colorSegment) {
        this.drawStringInternal(frameGraphics, Character.toString(symbol), colorSegment, -1, SANS_SERIF_FONT);
    }

    /**
     * Draws a string at the current location, and updates the pointer location.
     *
     * @param value The value to draw.
     */
    private void drawStringInternal(Graphics2D frameGraphics, @NotNull String value, @NotNull ColorSegment colorSegment, int originalCharIndex) {
        this.drawStringInternal(frameGraphics, value, colorSegment, originalCharIndex, this.currentFont);
    }

    private void drawStringInternal(Graphics2D frameGraphics, @NotNull String value, @NotNull ColorSegment colorSegment, int originalCharIndex, @NotNull Font font) {
        if (value.isEmpty()) {
            return;
        }

        String textToDraw = value;

        if (colorSegment.isObfuscated() && originalCharIndex != -1) {
            char originalChar = value.charAt(0);
            int originalWidth = (int) font.getStringBounds(String.valueOf(originalChar), frameGraphics.getFontRenderContext()).getWidth();

            // Select the correct map based on whether the segment is bold
            Map<Integer, List<Character>> widthMap = OBFUSCATION_WIDTH_MAPS.get(colorSegment.isBold());
            List<Character> matchingWidthChars = widthMap.get(originalWidth);

            if (matchingWidthChars != null && !matchingWidthChars.isEmpty()) {
                textToDraw = String.valueOf(matchingWidthChars.get(ThreadLocalRandom.current().nextInt(matchingWidthChars.size())));
            } else {
                // Fallback: If no char with matching width is found, use the original char
                // We could use something else but the original seems safer to avoid potential layout shifting
                log.warn("No matching character found with width {} for original character '{}', using original", originalWidth, originalChar);
                textToDraw = String.valueOf(originalChar);
            }
        }

        frameGraphics.setFont(font);

        int nextBounds = (int) font.getStringBounds(textToDraw, frameGraphics.getFontRenderContext()).getWidth();

        // Draw Strikethrough Drop Shadow
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(frameGraphics, nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, true);
        }

        // Draw Underlined Drop Shadow
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, true);
        }

        // Draw Drop Shadow Text
        frameGraphics.setColor(this.currentColor.getBackgroundColor());
        frameGraphics.drawString(textToDraw, this.locationX + PIXEL_SIZE, this.locationY + PIXEL_SIZE);

        // Draw Text
        frameGraphics.setColor(this.currentColor.getColor());
        frameGraphics.drawString(textToDraw, this.locationX, this.locationY);

        // Draw Strikethrough
        if (colorSegment.isStrikethrough()) {
            this.drawThickLineInternal(frameGraphics, nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, false);
        }

        // Draw Underlined
        if (colorSegment.isUnderlined()) {
            this.drawThickLineInternal(frameGraphics, nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, false);
        }

        // Update Draw Pointer Location
        this.locationX += nextBounds;
    }

    /**
     * Draws a thick line on the image.
     *
     * @param frameGraphics The graphics context to draw on.
     * @param width      The width of the line.
     * @param xPosition  The x position to draw the line.
     * @param yPosition  The y position to draw the line.
     * @param xOffset    The x offset to apply to the line.
     * @param yOffset    The y offset to apply to the line.
     * @param dropShadow Whether to draw a drop shadow.
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
     * Renders the tooltip. If any segment is obfuscated, it generates frames to create an animation.
     * Otherwise, it generates a single static image.
     */
    public MinecraftTooltip render() {
        this.isAnimated = false;
        this.animationFrames.clear();
        this.largestWidth = 0;

        BufferedImage dummyImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measureGraphics = dummyImage.createGraphics();
        this.drawLinesInternal(measureGraphics);
        measureGraphics.dispose();

        int finalWidth = this.largestWidth;
        int finalHeight = this.locationY - (Y_INCREMENT + (this.lines.isEmpty() || !this.paddingFirstLine ? 0 : PIXEL_SIZE * 2)) + START_XY + PIXEL_SIZE * 4;

        int framesToGenerate = this.isAnimated ? this.animationFrameCount : 1;

        for (int i = 0; i < framesToGenerate; i++) {
            int frameWidth = Math.max(1, finalWidth + START_XY + PIXEL_SIZE * 4);
            int frameHeight = Math.max(1, finalHeight);
            BufferedImage frameImage = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = frameImage.createGraphics();

            graphics.setColor(new Color(18, 3, 18, this.isAnimated ? 255 : this.getAlpha()));
            graphics.fillRect(
                PIXEL_SIZE * 2,
                PIXEL_SIZE * 2,
                frameImage.getWidth() - PIXEL_SIZE * 4,
                frameImage.getHeight() - PIXEL_SIZE * 4
            );

            this.drawLinesInternal(graphics);

            BufferedImage processedFrame = this.cropFrame(frameImage, finalWidth, finalHeight);

            if (this.renderBorder) {
                Graphics2D borderGraphics = processedFrame.createGraphics();
                this.drawBorders(borderGraphics, processedFrame.getWidth(), processedFrame.getHeight());
                borderGraphics.dispose();
            }

            processedFrame = this.addPadding(processedFrame);
            graphics.dispose();

            this.animationFrames.add(processedFrame);
        }

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
        private int alpha = 255;
        private int padding = 0;
        private boolean paddingFirstLine = true;
        private boolean renderBorder = true;
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

        public Builder withAlpha(int value) {
            this.alpha = Range.between(0, 255).fit(value);
            return this;
        }

        public Builder withDefaultColor(@NotNull ChatFormat chatColor) {
            this.defaultColor = chatColor;
            return this;
        }

        public Builder withEmptyLine() {
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
                this.frameDelayMs,
                this.animationFrameCount
            );
        }
    }
}