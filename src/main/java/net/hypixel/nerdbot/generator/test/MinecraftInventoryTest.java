package net.hypixel.nerdbot.generator.test;

import net.hypixel.nerdbot.generator.image.NewMinecraftInventory;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MinecraftInventoryTest {

    public static void main(String[] args) {
        MinecraftInventoryGenerator inventoryGenerator = new MinecraftInventoryGenerator.Builder()
            .withRows(5)
            .withSlotsPerRow(9)
            .build();
        NewMinecraftInventory inventory = inventoryGenerator.generate();
        inventory.drawItem(1, "diamond_pickaxe", true);

        BufferedImage image = inventory.getImage();

        try {
            File outputfile = new File("inventory.png");
            ImageIO.write(image, "png", outputfile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
