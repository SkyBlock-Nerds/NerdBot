package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.command.ItemGenCommand;
import net.hypixel.nerdbot.util.skyblock.MCColor;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MinecraftImage {
    private static final int PIXEL_SIZE = 2;
    private static final int START_XY = PIXEL_SIZE * 5;
    private static final int Y_INCREMENT = PIXEL_SIZE * 10;

    private final GraphicsEnvironment ge;
    private final Graphics2D g2d;
    private static boolean fontsRegistered = false;
    private static final Font[] minecraftFonts = new Font[4];
    private static Font sansSerif = null;

    private MCColor currentColor;
    private Font currentFont;
    private BufferedImage image;
    private int locationX = START_XY;
    private int locationY = START_XY + PIXEL_SIZE * 2 + Y_INCREMENT / 2;
    private int largestWidth = 0;
    private int alphaValue = 255;

    public MinecraftImage(int imageWidth, int linesToPrint, MCColor defaultColor, boolean transparent) {
        this.ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        // registers the static fonts
        for (Font font : minecraftFonts) {
            ge.registerFont(font);
        }

        if (transparent) {
            this.alphaValue = 245;
        }

        this.g2d = initG2D(imageWidth, linesToPrint * Y_INCREMENT + START_XY + PIXEL_SIZE * 4, transparent);
        this.currentColor = defaultColor;
    }

    public BufferedImage getImage() {
        return this.image;
    }

    /**
     * Creates an image, then initialized a Graphics2D object from that image.
     *
     * @return G2D object
     */
    private Graphics2D initG2D(int width, int height, boolean transparent) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = image.createGraphics();

        // drawing main background
        graphics.setColor(new Color(18, 3, 18, alphaValue));
        graphics.fillRect(PIXEL_SIZE * 2, PIXEL_SIZE * 2, width - PIXEL_SIZE * 4, height - PIXEL_SIZE * 4);

        return graphics;
    }

    /**
     * Creates the purple border (and border around that) around the image
     */
    public void createImageBorder() {
        final int width = image.getWidth();
        final int height = image.getHeight();

        // draws darker purple border around purple rectangle
        g2d.setColor(new Color(18, 3, 18, alphaValue));
        g2d.fillRect(0, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        g2d.fillRect(PIXEL_SIZE, 0, width - PIXEL_SIZE * 2, PIXEL_SIZE);
        g2d.fillRect(width - PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, height - PIXEL_SIZE * 2);
        g2d.fillRect(PIXEL_SIZE, height - PIXEL_SIZE, width - PIXEL_SIZE * 2, PIXEL_SIZE);

        // drawing the purple rectangle around the edge
        g2d.setColor(new Color(37, 0, 94, alphaValue));
        g2d.drawRect(PIXEL_SIZE, PIXEL_SIZE, width - PIXEL_SIZE * 2 - 1, height - PIXEL_SIZE * 2 - 1);
        g2d.drawRect(PIXEL_SIZE + 1, PIXEL_SIZE + 1, width - PIXEL_SIZE * 3 - 1, height - PIXEL_SIZE * 3 - 1);
    }

    /**
     * Crops the image down to closely fit the size taken up
     */
    public void cropImage() {
        image = image.getSubimage(0, 0, largestWidth + START_XY, image.getHeight());
    }

    /**
     * Draws the strings to the generated image.
     *
     * @param parsedString A string with valid color codes, such as through #parseDescription().
     */
    public void drawStrings(ArrayList<ArrayList<ColoredString>> parsedString) {
        for (ArrayList<ColoredString> line : parsedString) {
            for (ColoredString colorSegment : line) {
                // setting the font if it is meant to be bold or italicised
                currentFont = minecraftFonts[(colorSegment.isBold() ? 1 : 0) + (colorSegment.isItalic() ? 2 : 0)];
                g2d.setFont(currentFont);

                currentColor = colorSegment.getCurrentColor();

                StringBuilder subWord = new StringBuilder();
                String displayingLine = colorSegment.toString();

                // iterating through all the indexes of the current line until there is a character which cannot be displayed
                for (int charIndex = 0; charIndex < displayingLine.length(); charIndex++) {
                    char character = displayingLine.charAt(charIndex);
                    if (!currentFont.canDisplay(character)) {
                        drawStringWithBackground(subWord.toString());
                        subWord.setLength(0);
                        drawSymbol(character);
                        continue;
                    }
                    // We do this to prevent monospace bullshit
                    subWord.append(character);
                }

                drawStringWithBackground(subWord.toString());
            }
            newLine(parsedString.indexOf(line) == 0);
        }
    }

    /**
     * Draws a symbol on the image, and moves the locationX to where needed.
     *
     * @param symbol Symbol to be drawn.
     */
    private void drawSymbol(char symbol) {
        g2d.setFont(sansSerif);

        g2d.setColor(currentColor.getBackgroundColor());
        g2d.drawString(Character.toString(symbol), locationX + 2, locationY + 2);

        g2d.setColor(currentColor.getColor());
        g2d.drawString(Character.toString(symbol), locationX, locationY);

        locationX += sansSerif.getStringBounds(Character.toString(symbol), g2d.getFontRenderContext()).getWidth();
        g2d.setFont(currentFont);
    }

    /**
     * Draws a string at the current location, and moves the locationX to where needed.
     *
     * @param string String to print.
     */
    private void drawStringWithBackground(String string) {
        // drawing the drop shadow text
        g2d.setColor(currentColor.getBackgroundColor());
        g2d.drawString(string, locationX + 2, locationY + 2);
        // drawing the normal text
        g2d.setColor(currentColor.getColor());
        g2d.drawString(string, locationX, locationY);

        // Move the printing location depending on the size of the printed string
        locationX += currentFont.getStringBounds(string, g2d.getFontRenderContext()).getWidth();
    }

    /**
     * Moves the string to print to the next line.
     *
     * @param increaseGap - Increase number of pixels between next line
     */
    private void newLine(boolean increaseGap) {
        locationY += Y_INCREMENT;
        if (increaseGap) {
            locationY += PIXEL_SIZE * 2;
        }

        if (locationX > largestWidth) {
            largestWidth = locationX;
        }
        locationX = START_XY;
    }

    /**
     * Initializes a font.
     *
     * @param path The path to the font in the resources' folder.
     *
     * @return The initialized font.
     */
    @Nullable
    private static Font initFont(String path, float size) {
        Font font;
        try {
            InputStream fontStream = ItemGenCommand.class.getResourceAsStream(path);
            if (fontStream == null) {
                return null;
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
            return null;
        }
        return font;
    }

    /**
     * Initializes all required fonts before executing
     *
     * @return If registering fonts was successful
     */
    public static boolean registerFonts() {
        if (fontsRegistered)
            return true;

        if (minecraftFonts[0] == null) {
            minecraftFonts[0] = initFont("/Minecraft/minecraft.otf", 15.5f);
        }
        if (minecraftFonts[1] == null) {
            minecraftFonts[1] = initFont("/Minecraft/3_Minecraft-Bold.otf", 20.5f);
        }
        if (minecraftFonts[2] == null) {
            minecraftFonts[2] = initFont("/Minecraft/2_Minecraft-Italic.otf", 20.5f);
        }
        if (minecraftFonts[3] == null) {
            minecraftFonts[3] = initFont("/Minecraft/4_Minecraft-BoldItalic.otf", 20.5f);
        }
        if (sansSerif == null) {
            sansSerif = new Font("SansSerif", Font.PLAIN, 20);
        }

        fontsRegistered = minecraftFonts[0] != null && minecraftFonts[1] != null && minecraftFonts[2] != null && minecraftFonts[3] != null;
        return fontsRegistered;
    }
}
