package net.hypixel.nerdbot.generator.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.text.ChatFormat;
import net.hypixel.nerdbot.generator.text.segment.ColorSegment;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;
import net.hypixel.nerdbot.util.Range;
import net.hypixel.nerdbot.util.Util;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Log4j2
public class MinecraftTooltip {

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
    }

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
    @Getter(AccessLevel.PRIVATE)
    private final Graphics2D graphics;
    @Getter
    private BufferedImage image;
    @Getter
    private ChatFormat currentColor;
    private Font currentFont;
    // Positioning & Size
    private int locationX = START_XY;
    private int locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
    private int largestWidth = 0;

    private MinecraftTooltip(List<LineSegment> lines, ChatFormat defaultColor, int alpha, int padding, boolean paddingFirstLine, boolean renderBorder) {
        this.alpha = alpha;
        this.padding = padding;
        this.paddingFirstLine = paddingFirstLine;
        this.lines = lines;
        int lineLength = lines.stream()
            .mapToInt(LineSegment::length)
            .max()
            .orElse(LINE_LENGTH.getMaximum());
        this.graphics = this.initG2D(LINE_LENGTH.fit(lineLength) * 25, this.lines.size() * Y_INCREMENT + START_XY + PIXEL_SIZE * 4);
        this.currentColor = defaultColor;
        this.renderBorder = renderBorder;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an image, then initialized a Graphics2D object from that image.
     *
     * @return G2D object
     */
    private Graphics2D initG2D(int width, int height) {
        // Create Image
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw Primary Background
        Graphics2D graphics = this.getImage().createGraphics();
        graphics.setColor(new Color(18, 3, 18, this.getAlpha()));
        graphics.fillRect(
            PIXEL_SIZE * 2,
            PIXEL_SIZE * 2,
            width - PIXEL_SIZE * 4,
            height - PIXEL_SIZE * 4
        );

        return graphics;
    }

    /**
     * Crops the image to fit the space taken up by the borders.
     */
    public void cropImage() {
        this.image = this.getImage().getSubimage(
            0,
            0,
            this.largestWidth + START_XY,
            this.getImage().getHeight()
        );
    }

    /**
     * Resizes the image to add padding.
     */
    public void addPadding() {
        if (this.getPadding() > 0) {
            BufferedImage resizedImage = new BufferedImage(
                this.getImage().getWidth() + this.getPadding() * 2,
                this.getImage().getHeight() + this.getPadding() * 2,
                BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D graphics2D = resizedImage.createGraphics();
            graphics2D.drawImage(this.getImage(), this.getPadding(), this.getPadding(), this.getImage().getWidth(), this.getImage().getHeight(), null);
            graphics2D.dispose();
            this.image = resizedImage;
        }
    }

    /**
     * Creates the inner and outer purple borders around the image.
     */
    public void drawBorders() {
        final int width = this.getImage().getWidth();
        final int height = this.getImage().getHeight();

        // Draw Darker Purple Border Around Purple Border
        this.getGraphics().setColor(new Color(18, 3, 18, this.getAlpha()));
        this.getGraphics().fillRect(0, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        this.getGraphics().fillRect(PIXEL_SIZE, 0, width - PIXEL_SIZE * 2, PIXEL_SIZE);
        this.getGraphics().fillRect(width - PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        this.getGraphics().fillRect(PIXEL_SIZE, height - PIXEL_SIZE, width - PIXEL_SIZE * 2, PIXEL_SIZE);

        // Draw Purple Border
        this.getGraphics().setColor(new Color(37, 0, 94, this.getAlpha()));
        this.getGraphics().drawRect(PIXEL_SIZE, PIXEL_SIZE, width - PIXEL_SIZE * 2 - 1, height - PIXEL_SIZE * 2 - 1);
        this.getGraphics().drawRect(PIXEL_SIZE + 1, PIXEL_SIZE + 1, width - PIXEL_SIZE * 3 - 1, height - PIXEL_SIZE * 3 - 1);
    }

    /**
     * Draws the lines on the image.
     */
    public void drawLines() {
        this.getLines().forEach(line -> {
            line.getSegments().forEach(segment -> {
                // Change Fonts and Color
                this.currentFont = MINECRAFT_FONTS.get((segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0));
                this.getGraphics().setFont(this.currentFont);
                this.currentColor = segment.getColor().orElse(ChatFormat.GRAY);

                StringBuilder subWord = new StringBuilder();
                String segmentText = segment.getText();

                // Iterate through all characters on the current segment until there is a character which cannot be displayed
                for (int charIndex = 0; charIndex < segmentText.length(); charIndex++) {
                    char character = segmentText.charAt(charIndex);

                    if (!this.currentFont.canDisplay(character)) {
                        this.drawString(subWord.toString(), segment);
                        subWord.setLength(0);
                        this.drawSymbol(character, segment);
                        continue;
                    }

                    // Prevent Monospace
                    subWord.append(character);
                }

                this.drawString(subWord.toString(), segment);
            });

            this.updatePositionAndSize(this.getLines().indexOf(line) == 0 && this.isPaddingFirstLine());
        });
    }

    /**
     * Draws a symbol on the image, and updates the pointer location.
     *
     * @param symbol The symbol to draw.
     */
    private void drawSymbol(char symbol, @NotNull ColorSegment colorSegment) {
        this.drawString(Character.toString(symbol), colorSegment, SANS_SERIF_FONT);
    }

    /**
     * Draws a string at the current location, and updates the pointer location.
     *
     * @param value The value to draw.
     */
    private void drawString(@NotNull String value, @NotNull ColorSegment colorSegment) {
        this.drawString(value, colorSegment, this.currentFont);
    }

    private void drawString(@NotNull String value, @NotNull ColorSegment colorSegment, @NotNull Font font) {
        // Change Font
        this.getGraphics().setFont(font);

        // Next Draw Position
        int nextBounds = (int) font.getStringBounds(value, this.getGraphics().getFontRenderContext()).getWidth();

        // Draw Strikethrough Drop Shadow
        if (colorSegment.isStrikethrough())
            this.drawThickLine(nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, true);

        // Draw Underlined Drop Shadow
        if (colorSegment.isUnderlined())
            this.drawThickLine(nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, true);

        // Draw Drop Shadow Text
        this.getGraphics().setColor(this.currentColor.getBackgroundColor());
        this.getGraphics().drawString(value, this.locationX + PIXEL_SIZE, this.locationY + PIXEL_SIZE);

        // Draw Text
        this.getGraphics().setColor(this.currentColor.getColor());
        this.getGraphics().drawString(value, this.locationX, this.locationY);

        // Draw Strikethrough
        if (colorSegment.isStrikethrough())
            this.drawThickLine(nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, false);

        // Draw Underlined
        if (colorSegment.isUnderlined())
            this.drawThickLine(nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, false);

        // Update Draw Pointer Location
        this.locationX += nextBounds;

        // Reset Font
        this.getGraphics().setFont(this.currentFont);
    }

    private void drawThickLine(int width, int xPosition, int yPosition, int xOffset, int yOffset, boolean dropShadow) {
        int xPosition1 = xPosition;
        int xPosition2 = xPosition + width + xOffset;
        yPosition += yOffset;

        if (dropShadow) {
            xPosition1 += PIXEL_SIZE;
            xPosition2 += PIXEL_SIZE;
            yPosition += PIXEL_SIZE;
        }

        this.getGraphics().setColor(dropShadow ? this.currentColor.getBackgroundColor() : this.currentColor.getColor());
        this.getGraphics().drawLine(xPosition1, yPosition, xPosition2, yPosition);
        this.getGraphics().drawLine(xPosition1, yPosition + 1, xPosition2, yPosition + 1);
    }

    /**
     * Draws lines, resizes the image and draws borders.
     */
    public MinecraftTooltip render() {
        this.drawLines();
        this.cropImage();

        if (this.renderBorder) {
            this.drawBorders();
        }

        this.addPadding();
        return this;
    }

    @SneakyThrows
    public InputStream toStream() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(this.getImage(), "PNG", outputStream);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    public File toFile() {
        try {
            File tempFile = new File(SystemUtils.getJavaIoTmpDir(), String.format("%s.png", UUID.randomUUID()));
            ImageIO.write(this.getImage(), "PNG", tempFile);
            return tempFile;
        } catch (IOException exception) {
            log.error("Unable to write image to file!", exception);
        }

        return null;
    }

    /**
     * Moves the pointer to draw on the next line.
     *
     * @param increaseGap Increase number of pixels between the next line
     */
    private void updatePositionAndSize(boolean increaseGap) {
        this.locationY += Y_INCREMENT + (increaseGap ? PIXEL_SIZE * 2 : 0);
        this.largestWidth = Math.max(this.locationX, this.largestWidth);
        this.locationX = START_XY;
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

        @Override
        public @NotNull MinecraftTooltip build() {
            return new MinecraftTooltip(
                this.lines,
                this.defaultColor,
                this.alpha,
                this.padding,
                this.paddingFirstLine,
                this.renderBorder
            );
        }
    }
}