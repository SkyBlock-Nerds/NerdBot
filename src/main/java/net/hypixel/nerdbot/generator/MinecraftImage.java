package net.hypixel.nerdbot.generator;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import net.hypixel.nerdbot.command.ItemGenCommand;
import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Stat;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class MinecraftImage {
    int locationX = 10;
    int locationY = 25;

    GraphicsEnvironment ge;
    Graphics2D g2d;
    final Font minecraftFont;
    final Font minecraftBold;
    MCColor currentColor;
    boolean boldFlag = false; //True if we're currently printing with bold, false if not.
    private BufferedImage image;

    public MinecraftImage(int imageWidth, int linesToPrint, MCColor defaultColor) {
        this.ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.minecraftFont = initFont("/Minecraft/minecraft.ttf", 16f);
        this.minecraftBold = initFont("/Minecraft/3_Minecraft-Bold.otf", 22f);
        this.g2d = initG2D(imageWidth, linesToPrint * 23);
        this.currentColor = defaultColor;
    }

    public MinecraftImage(int imageWidth, int linesToPrint) {
        this.ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.minecraftFont = initFont("/Minecraft/minecraft.ttf", 16f);
        this.minecraftBold = initFont("/Minecraft/3_Minecraft-Bold.otf", 22f);
        this.g2d = initG2D(imageWidth, linesToPrint * 23);
        this.currentColor = MCColor.GRAY;
    }

    public BufferedImage getImage() {
        return this.image;
    }

    /**
     * Prints the strings to the generated image.
     * @param parsedString A string with valid color codes, such as through #parseDescription().
     */
    public void printStrings(ArrayList<String> parsedString) {
        for (String line : parsedString) {
            locationX = 10;

            //Let's iterate through each character in our line, looking for colors
            StringBuilder subWord = new StringBuilder();
            for(int colorStartIndex = 0; colorStartIndex < line.length(); colorStartIndex++) {
                //Check for colors
                if ((colorStartIndex + 2 < line.length()) && (line.charAt(colorStartIndex) == '%') && (line.charAt(colorStartIndex + 1) == '%')) {
                    int colorEndIndex = -1;

                    //Get index of where color code ends.
                    for(int j = colorStartIndex + 1; j < line.length() - 2; j++) {
                        if (line.charAt(j + 1) == '%' && line.charAt(j + 2) == '%') {
                            colorEndIndex = j;
                            break;
                        }
                    }

                    if (colorEndIndex != -1) {
                        //We've previously verified that this is a good color, so let's trust it
                        drawStringWithBackground(subWord.toString());
                        subWord.setLength(0);

                        String foundColor = line.substring(colorStartIndex + 2, colorEndIndex + 1);

                        if (foundColor.equalsIgnoreCase("bold")) {
                            drawStringWithBackground(subWord.toString());

                            subWord.setLength(0);
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftBold);
                            boldFlag = true;
                        } else {
                            for (MCColor color : MCColor.values()) {
                                if (foundColor.equalsIgnoreCase(color.toString())) {
                                    currentColor = color;
                                }
                            }
                            g2d.setColor(currentColor.getColor());
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftFont);
                            boldFlag = false;
                        }
                    }
                } else if (!minecraftFont.canDisplay(line.charAt(colorStartIndex))) {
                    //We need to draw this character special, so let's get rid of our old word.
                    drawStringWithBackground(subWord.toString());
                    subWord.setLength(0);
                    drawSymbol(line.charAt(colorStartIndex));
                } else {
                    //We do this to prevent monospace bullshit
                    subWord.append(line.charAt(colorStartIndex));
                }
            }

            drawStringWithBackground(subWord.toString());
            newLine();
        }

    }

    /**
     * Draws a symbol on the image, and moves the locationX to where needed.
     * @param symbol Symbol to be drawn.
     */
    private void drawSymbol(char symbol) {
        Font sansSerif = new Font("SansSerif", Font.PLAIN, 20);
        g2d.setFont(sansSerif);

        g2d.setColor(currentColor.getBackgroundColor());
        g2d.drawString(Character.toString(symbol), locationX + 2, locationY + 2);

        g2d.setColor(currentColor.getColor());
        g2d.drawString(Character.toString(symbol), locationX, locationY);

        locationX += sansSerif.getStringBounds(Character.toString(symbol), g2d.getFontRenderContext()).getWidth();
        g2d.setFont(boldFlag ? minecraftBold : minecraftFont);
    }

    /**
     * Draws a string at the current location, and moves the locationX to where needed.
     * @param string String to print.
     */
    private void drawStringWithBackground(String string) {
        g2d.setColor(currentColor.getBackgroundColor());
        g2d.drawString(string, locationX + 2, locationY + 2);
        g2d.setColor(currentColor.getColor());
        g2d.drawString(string, locationX, locationY);

        //Move the printing location depending on the size of the printed string
        if (boldFlag) {
            locationX += minecraftBold.getStringBounds(string, g2d.getFontRenderContext()).getWidth();
        } else {
            locationX += minecraftFont.getStringBounds(string, g2d.getFontRenderContext()).getWidth();
        }
    }

    /**
     * Moves the string to print to the next line.
     */
    private void newLine() {
        locationY += 23;
    }

    /**
     * Creates an image, then initialized a Graphics2D object from that image.
     * @return G2D object
     */
    private Graphics2D initG2D(int width, int height) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        return image.createGraphics();
    }

    /**
     * Initializes a font.
     * @param path The path to the font in the resources folder.
     * @return The initialized font.
     */
    @Nullable
    private Font initFont(String path, float size) {
        Font font;
        try {
            InputStream fontStream = ItemGenCommand.class.getResourceAsStream(path);
            if (fontStream == null) {
                throw new NullPointerException();
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
            ge.registerFont(font);
        } catch (IOException | FontFormatException | NullPointerException e) {
            e.printStackTrace();
            return null;
        }
        return font;
    }
}
