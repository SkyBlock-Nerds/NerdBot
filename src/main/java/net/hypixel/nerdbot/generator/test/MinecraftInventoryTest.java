package net.hypixel.nerdbot.generator.test;

import net.hypixel.nerdbot.generator.image.MinecraftInventoryImage;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class MinecraftInventoryTest {

    public static void main(String[] args) {
        MinecraftInventoryGenerator inventoryGenerator = new MinecraftInventoryGenerator.Builder()
            .withRows(5)
            .withSlotsPerRow(9)
            .build();
        MinecraftInventoryImage inventory = inventoryGenerator.generate();

        inventory.drawItem(1, "stone", true);

        try {
            File outputfile = new File("inventory.png");
            ImageIO.write(inventory.getImage(), "png", outputfile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
