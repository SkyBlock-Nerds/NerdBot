package net.hypixel.nerdbot.generator.image;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.impl.MinecraftItemGenerator;
import net.hypixel.nerdbot.generator.impl.MinecraftPlayerHeadGenerator;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.item.InventorySlot;
import net.hypixel.nerdbot.generator.parser.inventory.InventoryStringParser;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator.PIXELS_PER_PIXEL;
import static net.hypixel.nerdbot.generator.impl.MinecraftInventoryGenerator.SLOT_DIMENSION;
import static net.hypixel.nerdbot.util.Util.initFont;

@Getter
@Log4j2
public class MinecraftInventoryImage extends GeneratedObject implements Generator {

    private static final Font MINECRAFT_FONT;
    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static final Color BORDER_COLOR = new Color(198, 198, 198);
    private final int rows;
    private final int slotsPerRow;
    private final int totalSlots;
    private final List<InventorySlot> items;
    private final Graphics2D g2d;

    static {
        MINECRAFT_FONT = initFont("/minecraft/fonts/minecraft.otf", PIXELS_PER_PIXEL * 8);
        if (MINECRAFT_FONT != null) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(MINECRAFT_FONT);
        }
    }

    public MinecraftInventoryImage(int rows, int slotsPerRow, List<InventorySlot> items, BufferedImage image) {
        super(image);
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.items = items;

        this.g2d = this.initG2D();
        this.totalSlots = rows * slotsPerRow;
    }

    private Graphics2D initG2D() {
        Graphics2D g2d = this.image.createGraphics();
        g2d.setFont(MINECRAFT_FONT);
        return g2d;
    }

    public void setTitle(String title) {
        if (title != null) {
            this.g2d.setColor(DROP_SHADOW_COLOR);
            this.g2d.drawString(title, 8 * PIXELS_PER_PIXEL, this.items.get(0).getImageCoordinates().getY() - PIXELS_PER_PIXEL * 4);
        }
    }

    public void drawItems(String inventoryData) {
        InventoryStringParser parser = new InventoryStringParser(this.totalSlots);
        ArrayList<InventoryItem> items = parser.parse(inventoryData);

        for (InventoryItem parsedItem : items) {
            if (parsedItem.getItemName().contains("player_head")) {
                MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(parsedItem.getExtraContent())
                    .build();
                BufferedImage playerHeadImage = playerHeadGenerator.generate().getImage();

                parsedItem.setImage(playerHeadImage);
            } else if (!parsedItem.getItemName().equalsIgnoreCase("null")) {
                MinecraftItemGenerator itemGenerator = new MinecraftItemGenerator.Builder()
                    .withItem(parsedItem.getItemName())
                    .isEnchanted(parsedItem.getExtraContent() != null && parsedItem.getExtraContent().contains("enchant"))
                    .withData(parsedItem.getExtraContent())
                    .build();

                BufferedImage itemImage = itemGenerator.generate().getImage();
                parsedItem.setImage(itemImage);
            }

            this.drawItem(parsedItem);
        }
    }

    private void drawItem(InventoryItem item) {
        // getting the item and its offset
        BufferedImage itemToDraw = item.getImage();
        int offset = itemToDraw != null ? Math.abs(itemToDraw.getWidth() - itemToDraw.getHeight()) / 2 : PIXELS_PER_PIXEL;
        if (itemToDraw == null) {
            this.g2d.setColor(BORDER_COLOR);
        }

        int[] slots = item.getSlot();
        int[] amounts = item.getAmount();

        for (int index = 0; index < slots.length; index++) {
            // Converts the index into an x/y coordinate
            ImageCoordinates slot = this.items.get(slots[index] - 1).getImageCoordinates();
            int x = slot.getX();
            int y = slot.getY();

            if (itemToDraw == null) {
                this.g2d.fillRect(x, y, SLOT_DIMENSION, SLOT_DIMENSION);
                continue;
            }

            this.g2d.drawImage(itemToDraw,
                x + PIXELS_PER_PIXEL,
                y + PIXELS_PER_PIXEL,
                x + SLOT_DIMENSION - PIXELS_PER_PIXEL,
                y + SLOT_DIMENSION - PIXELS_PER_PIXEL,
                -offset,
                0,
                itemToDraw.getWidth() + offset,
                itemToDraw.getHeight(),
                null
            );

            // Check if we need to draw the amount
            if (amounts[index] == 1) {
                continue;
            }

            // Set the text coordinates to the bottom right
            int textX = x + SLOT_DIMENSION - PIXELS_PER_PIXEL;
            int textY = y + SLOT_DIMENSION - PIXELS_PER_PIXEL;

            // Move the cursor to be writing as right justified
            int startBounds = (int) MINECRAFT_FONT.getStringBounds(String.valueOf(amounts[index]), this.g2d.getFontRenderContext()).getWidth();
            startBounds -= PIXELS_PER_PIXEL;

            // Draw the amount number with the drop shadow at the slot position
            this.g2d.setColor(DROP_SHADOW_COLOR);
            this.g2d.drawString(String.valueOf(amounts[index]), textX - startBounds + PIXELS_PER_PIXEL, textY + PIXELS_PER_PIXEL);
            this.g2d.setColor(NORMAL_TEXT_COLOR);
            this.g2d.drawString(String.valueOf(amounts[index]), textX - startBounds, textY);
        }
    }

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(this.getImage());
    }
}
