package net.hypixel.nerdbot.generator.image;

import lombok.Getter;
import net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.InventoryItem;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

@Getter
public class MinecraftInventoryImage extends GeneratedObject {

    private final int rows;
    private final int slotsPerRow;
    private final List<InventoryItem> items;

    public MinecraftInventoryImage(int rows, int slotsPerRow, List<InventoryItem> items) {
        super(null);
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.items = items;
    }

    public MinecraftInventoryImage(int rows, int slotsPerRow, List<InventoryItem> items, BufferedImage image) {
        super(image);
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.items = items;
    }

    public InventoryItem getItem(int slot) {
        return items.get(slot);
    }

    public void setItem(int slot, InventoryItem item) {
        items.add(slot, item);
    }

    public void drawItem(int slot, String itemId, boolean enchanted) {
        BufferedImage item = new MinecraftItemGenerator.Builder()
            .withItem(itemId)
            .isEnchanted(enchanted)
            .build()
            .generate()
            .getImage();

        /*if (item.getHeight() > 16 && item.getWidth() > 16) {
            item = ImageUtil.resizeImage(item, 16, 16);
        }*/

        ImageCoordinates slotCoordinates = getSlotCoordinates(slot);

        Graphics2D g2d = image.createGraphics();
        g2d.drawImage(item, slotCoordinates.getX(), slotCoordinates.getY(), null);
        g2d.dispose();
    }

    public ImageCoordinates getSlotCoordinates(int slot) {
        int row = slot / slotsPerRow;
        int column = slot % slotsPerRow;
        int x = column * MinecraftInventoryGenerator.SINGLE_SLOT_IMAGE.getWidth();
        int y = row * MinecraftInventoryGenerator.SINGLE_SLOT_IMAGE.getHeight();

        return new ImageCoordinates(x, y);
    }
}
