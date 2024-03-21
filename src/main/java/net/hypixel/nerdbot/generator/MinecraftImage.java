package net.hypixel.nerdbot.generator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.util.ColoredString;
import net.hypixel.nerdbot.util.FontUtil;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static net.hypixel.nerdbot.util.Util.initFont;

@Log4j2
public class MinecraftImage {

    private static final int PIXEL_SIZE = 2;
    private static final int START_XY = PIXEL_SIZE * 5;
    private static final int Y_INCREMENT = PIXEL_SIZE * 10;
    private static final int STRIKETHROUGH_OFFSET = -8;
    private static final int UNDERLINE_OFFSET = 2;
    private static final Font FALLBACK_FONT = initFont("/minecraft_assets/fonts/unifont-15.1.05.otf", 15.5f);
    private static final Font[] COMIC_SANS = new Font[]{
        initFont("/minecraft_assets/fonts/COMICSANS.TTF", 20.0f),
        initFont("/minecraft_assets/fonts/COMICSANSBOLD.TTF", 20.0f),
        initFont("/minecraft_assets/fonts/COMICSANSITALIC.TTF", 20.0f),
        initFont("/minecraft_assets/fonts/COMICSANSBOLDITALIC.TTF", 20.0f)
    };
    private static final Font[] MINECRAFT_FONTS = new Font[]{
        initFont("/minecraft_assets/fonts/minecraft.otf", 15.5f),
        initFont("/minecraft_assets/fonts/3_Minecraft-Bold.otf", 20.0f),
        initFont("/minecraft_assets/fonts/2_Minecraft-Italic.otf", 20.5f),
        initFont("/minecraft_assets/fonts/4_Minecraft-BoldItalic.otf", 20.5f)
    };

    private static boolean fontsRegisteredCorrectly = true;

    static {
        Font[] allFonts = new Font[MINECRAFT_FONTS.length + 1];
        System.arraycopy(COMIC_SANS, 0, allFonts, 0, COMIC_SANS.length);
        allFonts[MINECRAFT_FONTS.length] = FALLBACK_FONT;

        // Register Fonts
        for (Font font : allFonts) {
            if (font == null) {
                fontsRegisteredCorrectly = false;
                break;
            }
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            log.info("Registered Font: " + font.getFontName());
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
                if (Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1) {
                    currentFont = COMIC_SANS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
                }

                currentColor = segment.getCurrentColor();

                StringBuilder subWord = new StringBuilder();
                String displayingLine = segment.toString();

                for (int charIndex = 0; charIndex < displayingLine.length(); charIndex++) {
                    char character = displayingLine.charAt(charIndex);

                    boolean isSymbol = !Character.isLetterOrDigit(character)
                        && !Character.isWhitespace(character)
                        && !Character.isSpaceChar(character)
                        && !Character.isISOControl(character);

                    if (!FontUtil.canRenderCharacter(MINECRAFT_FONTS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)], character)) {
                        this.currentFont = FALLBACK_FONT;
                    } else {
                        this.currentFont = MINECRAFT_FONTS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
                    }
                    this.getGraphics().setFont(this.currentFont);

                    if (isSymbol) {
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
        this.drawString(Character.toString(symbol), segment);
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
        // Get the Graphics object
        Graphics2D graphics = this.getGraphics();
        graphics.setFont(font);

        // Initialize position variables
        int x = this.locationX;
        int y = this.locationY;

        // Draw Strikethrough Drop Shadow
        if (segment.isStrikethrough()) {
            drawThickLine(graphics, x, y, -1, STRIKETHROUGH_OFFSET, true);
        }

        // Draw Underlined Drop Shadow
        if (segment.isUnderlined()) {
            drawThickLine(graphics, x - PIXEL_SIZE, y, 1, UNDERLINE_OFFSET, true);
        }

        // Set colors for text drawing
        graphics.setColor(this.currentColor.getBackgroundColor());
        Color textColor = this.currentColor.getColor();

        // Loop through each character and draw it individually
        for (char c : value.toCharArray()) {
            boolean canRender = FontUtil.canRenderCharacter(MINECRAFT_FONTS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)], c);

            if (Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1) {
                if (FontUtil.canRenderCharacter(COMIC_SANS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)], c)) {
                    this.currentFont = COMIC_SANS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
                } else {
                    this.currentFont = FALLBACK_FONT;
                }
            } else if (!canRender) {
                this.currentFont = FALLBACK_FONT;
            } else {
                this.currentFont = MINECRAFT_FONTS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
            }

            graphics.setFont(this.currentFont);

            String character = String.valueOf(c);
            FontMetrics metrics = graphics.getFontMetrics();
            int charWidth = metrics.charWidth(c);

            // Draw Drop Shadow Text
            graphics.setColor(this.currentColor.getBackgroundColor());
            graphics.drawString(character, x + PIXEL_SIZE, y + PIXEL_SIZE);

            // Draw Text
            graphics.setColor(textColor);
            graphics.drawString(character, x, y);

            // Update x position for the next character
            x += charWidth;

            // Reset font to Minecraft font
            this.currentFont = MINECRAFT_FONTS[(segment.isBold() ? 1 : 0) + (segment.isItalic() ? 2 : 0)];
            graphics.setFont(this.currentFont);
        }

        // Draw Strikethrough
        if (segment.isStrikethrough()) {
            drawThickLine(graphics, x, y, -1, STRIKETHROUGH_OFFSET, false);
        }

        // Draw Underlined
        if (segment.isUnderlined()) {
            drawThickLine(graphics, x - PIXEL_SIZE, y, 1, UNDERLINE_OFFSET, false);
        }

        // Update Draw Pointer Location
        this.locationX = x;
    }


    private void drawThickLine(Graphics2D graphics, int xPosition, int yPosition, int xOffset, int yOffset, boolean dropShadow) {
        int xPosition1 = xPosition;
        int xPosition2 = xPosition + xOffset;
        yPosition += yOffset;

        if (dropShadow) {
            xPosition1 += PIXEL_SIZE;
            xPosition2 += PIXEL_SIZE;
            yPosition += PIXEL_SIZE;
        }

        graphics.setColor(dropShadow ? this.currentColor.getBackgroundColor() : this.currentColor.getColor());
        graphics.drawLine(xPosition1, yPosition, xPosition2, yPosition);
        graphics.drawLine(xPosition1, yPosition + 1, xPosition2, yPosition + 1);
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
