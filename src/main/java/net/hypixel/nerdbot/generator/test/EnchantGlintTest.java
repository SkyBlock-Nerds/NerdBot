package net.hypixel.nerdbot.generator.test;

import net.hypixel.nerdbot.generator.util.GifSequenceWriter;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.spritesheet.ItemSpritesheet;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EnchantGlintTest {

    public static void main(String[] args) {
        try {
            BufferedImage itemImage = ItemSpritesheet.getTexture("diamond_pickaxe");
            itemImage = ImageUtil.upscaleImage(itemImage, 20);

            BufferedImage glintImage = ImageIO.read(new File("src/main/resources/minecraft/textures/overlays.png"));
            glintImage = glintImage.getSubimage(0, 16, glintImage.getWidth(), glintImage.getHeight() - 16);

            int itemWidth = itemImage.getWidth();
            int itemHeight = itemImage.getHeight();
            int glintWidth = glintImage.getWidth();
            int glintHeight = glintImage.getHeight();

            List<BufferedImage> frames = new ArrayList<>();
            int numFrames = 360;
            int frameDuration = 50; // In milliseconds

            for (int i = 0; i < numFrames; i++) {
                BufferedImage frame = new BufferedImage(itemWidth, itemHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = frame.createGraphics();

                // Calculate the angle for the glint effect to rotate clockwise
                double angle = Math.toRadians(i);

                // Draw the item image as the background
                g.drawImage(itemImage, 0, 0, null);

                // Apply a rotation transformation to the glint image
                AffineTransform transform = new AffineTransform();
                transform.rotate(angle, itemWidth / 2.0, itemHeight / 2.0);
                g.setTransform(transform);

                // Draw the glint image using the SRC_OVER composite
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g.drawImage(glintImage, (itemWidth - glintWidth) / 2, (itemHeight - glintHeight) / 2, null);

                g.dispose();
                frames.add(frame);
            }

            // Save the frames to a GIF file
            ImageOutputStream output = new FileImageOutputStream(new File("src/main/resources/minecraft/textures/enchanted_item.gif"));
            GifSequenceWriter writer = new GifSequenceWriter(output, frames.get(0).getType(), frameDuration, true);

            for (BufferedImage frame : frames) {
                writer.writeToSequence(frame);
            }

            writer.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
