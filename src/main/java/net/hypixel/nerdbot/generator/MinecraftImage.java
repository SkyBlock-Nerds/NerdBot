package net.hypixel.nerdbot.generator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.generator.util.ColoredString;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static net.hypixel.nerdbot.util.Util.initFont;

public class MinecraftImage {

    private static final int PIXEL_SIZE = 2;
    private static final int START_XY = PIXEL_SIZE * 5;
    private static final int Y_INCREMENT = PIXEL_SIZE * 10;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;

    private static final Font[] minecraftFonts;
    private static final Font sansSerif;
    private static boolean fontsRegisteredCorrectly = true;

    static {
        sansSerif = new Font("SansSerif", Font.PLAIN, 20);
        minecraftFonts = new Font[]{
            initFont("/minecraft_assets/fonts/minecraft.otf", 15.5f),
            initFont("/minecraft_assets/fonts/3_Minecraft-Bold.otf", 20.0f),
            initFont("/minecraft_assets/fonts/2_Minecraft-Italic.otf", 20.5f),
            initFont("/minecraft_assets/fonts/4_Minecraft-BoldItalic.otf", 20.5f)
        };

        // Register Minecraft Fonts
        for (Font font : minecraftFonts) {
            if (font == null) {
                fontsRegisteredCorrectly = false;
                break;
            }
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
        }
    }

    // Current Settings
    @Getter
    private final List<List<ColoredString>> lines;
    @Getter(AccessLevel.PRIVATE)
    private final ArrayList<Integer> lineWidths = new ArrayList<>();
    @Getter
    private final int alpha;
    @Getter
    private final int padding;
    @Getter
    private final boolean isNormalItem;
    @Getter
    private final boolean isCentered;
    @Getter(AccessLevel.PRIVATE)
    @Setter(AccessLevel.PRIVATE)
    private Graphics2D graphics;
    @Getter
    private BufferedImage image;
    @Getter
    private MCColor currentColor;
    private Font currentFont;
    // Positioning & Size
    private int locationX = START_XY;
    private int locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
    private int largestWidth = 0;

    public MinecraftImage(List<List<ColoredString>> lines, MCColor defaultColor, int defaultWidth, int alpha, int padding, boolean isNormalItem, boolean isCentered) {
        this.alpha = alpha;
        this.padding = padding;
        this.lines = lines;
        this.isNormalItem = isNormalItem;
        this.isCentered = isCentered;
        this.graphics = this.initG2D(defaultWidth + 2 * START_XY, this.lines.size() * Y_INCREMENT + START_XY + PIXEL_SIZE * 4 - (this.lines.size() == 1 ? PIXEL_SIZE : 0));
        this.currentColor = defaultColor;
    }

    public static boolean isFontsRegistered() {
        return fontsRegisteredCorrectly;
    }

    /**
     * Creates an image, then initializes a Graphics2D object from that image.
     *
     * @return G2D object
     */
    private Graphics2D initG2D(int width, int height) {
        // Create Image
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw Primary Background
        Graphics2D g2d = this.getImage().createGraphics();
        if (isNormalItem) {
            g2d.setColor(new Color(18, 3, 18, this.getAlpha()));
            g2d.fillRect(
                PIXEL_SIZE * 2,
                PIXEL_SIZE * 2,
                width - PIXEL_SIZE * 4,
                height - PIXEL_SIZE * 4
            );
        } else {
            g2d.setColor(new Color(0, 0, 0, this.getAlpha()));
            g2d.fillRect(0, 0, width, height);
        }


        return g2d;
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
        if (!this.isNormalItem) {
            return;
        }

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
     * Draws the strings to the generated image.
     */
    public void drawLines() {
        for (List<ColoredString> line : this.getLines()) {
            for (ColoredString segment : line) {
                // setting the font if it is meant to be bold or italicised
                currentFont = minecraftFonts[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
                this.getGraphics().setFont(currentFont);
                currentColor = segment.getCurrentColor();

                StringBuilder subWord = new StringBuilder();
                String displayingLine = segment.toString();

                // iterating through all the indexes of the current line until there is a character which cannot be displayed
                for (int charIndex = 0; charIndex < displayingLine.length(); charIndex++) {
                    char character = displayingLine.charAt(charIndex);

                    if (!currentFont.canDisplay(character)) {
                        this.drawString(subWord.toString(), segment);
                        subWord.setLength(0);
                        this.drawSymbol(character, segment);
                        continue;
                    }

                    // Prevent Monospace
                    subWord.append(character);
                }

                this.drawString(subWord.toString(), segment);
            }

            // increase size of first line if there are more than one lines present
            this.updatePositionAndSize(this.isNormalItem() && this.getLines().indexOf(line) == 0);
        }
    }

    /***
     * Centers the lines into the middle of the width of the screen
     */
    private void centerLines() {
        if (this.isNormalItem() || !this.isCentered()) {
            return;
        }

        BufferedImage centeredImage = new BufferedImage(this.image.getWidth(), this.image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = centeredImage.createGraphics();
        if (this.isNormalItem()) {
            g2d.setColor(new Color(18, 3, 18, this.getAlpha()));
            g2d.fillRect(
                PIXEL_SIZE * 2,
                PIXEL_SIZE * 2,
                this.image.getWidth() - PIXEL_SIZE * 4,
                this.image.getHeight() - PIXEL_SIZE * 4
            );
        }

        int currentY = START_XY;
        int imageMidpoint = this.image.getWidth() / 2 - START_XY / 2;

        for (int length : this.getLineWidths()) {
            int displacement = imageMidpoint - (length / 2);
            BufferedImage textTaken = this.image.getSubimage(START_XY, currentY, length, Y_INCREMENT);
            g2d.drawImage(textTaken, START_XY + displacement, currentY, length, Y_INCREMENT, null);

            currentY += Y_INCREMENT;
        }

        this.image = centeredImage;
        this.graphics = g2d;
    }

    /**
     * Draws a symbol on the image, and updates the pointer location.
     *
     * @param symbol The symbol to draw.
     */
    private void drawSymbol(char symbol, @NotNull ColoredString segment) {
        this.drawString(Character.toString(symbol), segment, sansSerif);
    }

    /**
     * Draws a string at the current location, and updates the pointer location.
     *
     * @param value The value to draw.
     */
    private void drawString(@NotNull String value, @NotNull ColoredString segment) {
        this.drawString(value, segment, this.currentFont);
    }

    private void drawString(@NotNull String value, @NotNull ColoredString segment, @NotNull Font font) {
        // Change Font
        this.getGraphics().setFont(font);

        // Next Draw Position
        int nextBounds = (int) font.getStringBounds(value, this.getGraphics().getFontRenderContext()).getWidth();

        // Draw Strikethrough Drop Shadow
        if (segment.isStrikethrough()) {
            this.drawThickLine(nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, true);
        }

        // Draw Underlined Drop Shadow
        if (segment.isUnderlined()) {
            this.drawThickLine(nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, true);
        }

        // Draw Drop Shadow Text
        this.getGraphics().setColor(this.currentColor.getBackgroundColor());
        this.getGraphics().drawString(value, this.locationX + PIXEL_SIZE, this.locationY + PIXEL_SIZE);

        // Draw Text
        this.getGraphics().setColor(this.currentColor.getColor());
        this.getGraphics().drawString(value, this.locationX, this.locationY);

        // Draw Strikethrough
        if (segment.isStrikethrough()) {
            this.drawThickLine(nextBounds, this.locationX, this.locationY, -1, STRIKETHROUGH_OFFSET, false);
        }

        // Draw Underlined
        if (segment.isUnderlined()) {
            this.drawThickLine(nextBounds, this.locationX - PIXEL_SIZE, this.locationY, 1, UNDERLINE_OFFSET, false);
        }

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
     * Draws the Lines, Resizes the Image and Draws the Borders.
     */
    public MinecraftImage render() {
        this.drawLines();
        this.cropImage();
        this.centerLines();
        this.drawBorders();
        this.addPadding();
        return this;
    }

    /**
     * Moves the pointer to draw on the next line.
     *
     * @param increaseGap Increase number of pixels between the next line
     */
    private void updatePositionAndSize(boolean increaseGap) {
        this.locationY += Y_INCREMENT + (increaseGap ? PIXEL_SIZE * 2 : 0);
        this.largestWidth = Math.max(this.locationX, this.largestWidth);
        this.getLineWidths().add(this.locationX);
        this.locationX = START_XY;
    }

}
