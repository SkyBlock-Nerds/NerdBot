package net.hypixel.nerdbot.generator.impl;

import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.inventory.InventoryStringParser;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.Range;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static net.hypixel.nerdbot.util.Util.initFont;

public class MinecraftInventoryGenerator implements Generator {

    public static final int MAX_ROWS_GENERATED = 18;
    public static final int MAX_COLUMNS_GENERATED = 18;
    public static final int PIXELS_PER_PIXEL = 2;
    public static final int PIXELS_PER_SLOT = 18;
    public static final int SLOT_DIMENSION = PIXELS_PER_SLOT * PIXELS_PER_PIXEL;
    public static final BufferedImage SINGLE_SLOT_IMAGE = new BufferedImage(SLOT_DIMENSION, SLOT_DIMENSION, BufferedImage.TYPE_INT_ARGB);
    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static final Color BORDER_COLOR = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);
    private static final Font MINECRAFT_FONT;

    static {
        try {
            BufferedImage singleSlotImage = ImageUtil.resizeImage(ImageIO.read(new File("src/main/resources/minecraft/spritesheets/slot.png")), SLOT_DIMENSION, SLOT_DIMENSION);
            SINGLE_SLOT_IMAGE.getGraphics().drawImage(singleSlotImage, 0, 0, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MINECRAFT_FONT = initFont("/minecraft/fonts/minecraft.otf", PIXELS_PER_PIXEL * 8);
        if (MINECRAFT_FONT != null) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(MINECRAFT_FONT);
        }
    }

    private final int rows;
    private final int slotsPerRow;
    private final String containerTitle;
    private final boolean drawTitle;
    private final boolean drawBorder;
    private final boolean drawBackground;
    private final int totalSlots;
    private final String inventoryString;

    private BufferedImage inventoryImage;
    private Graphics2D g2d;
    private int offset;
    private int topOffset;
    private List<ImageCoordinates> coordinates;

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString, boolean drawBorder, boolean drawBackground) {
        this(rows, slotsPerRow, containerTitle, inventoryString, drawBorder, drawBackground, null);
    }

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString, boolean drawBorder, boolean drawBackground, BufferedImage image) {
        this.inventoryImage = image;
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.containerTitle = containerTitle;
        this.inventoryString = inventoryString;
        this.drawTitle = containerTitle != null;
        this.drawBorder = drawBorder;
        this.drawBackground = drawBackground;

        this.totalSlots = rows * slotsPerRow;
        this.initializeGraphics();
    }

    private void initializeGraphics() {
        this.offset = drawBorder ? 7 * PIXELS_PER_PIXEL : 0;
        this.topOffset = offset + (drawTitle ? 13 * PIXELS_PER_PIXEL : 0) - (drawBorder && drawTitle ? 3 * PIXELS_PER_PIXEL : 0);

        if (this.inventoryImage == null) {
            int imageWidth = SLOT_DIMENSION * slotsPerRow + offset * 2;
            int imageHeight = SLOT_DIMENSION * rows + topOffset + offset;
            this.inventoryImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        }

        this.g2d = (Graphics2D) this.inventoryImage.getGraphics();
        this.g2d.setFont(MINECRAFT_FONT);
    }

    private void drawSlots() {
        coordinates = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            int yCoordinate = row * SLOT_DIMENSION + topOffset;
            for (int slot = 0; slot < slotsPerRow; slot++) {
                int xCoordinate = slot * SLOT_DIMENSION + offset;
                coordinates.add(new ImageCoordinates(xCoordinate, yCoordinate));

                if (drawBackground) {
                    g2d.drawImage(SINGLE_SLOT_IMAGE, xCoordinate, yCoordinate, null);
                }
            }
        }
    }

    private void drawBorder() {
        if (!this.drawBorder) {
            return;
        }

        int imageWidth = this.inventoryImage.getWidth();
        int imageHeight = this.inventoryImage.getHeight();

        // drawing the overall background
        this.g2d.setColor(BORDER_COLOR);
        this.g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, imageWidth - PIXELS_PER_PIXEL * 6, imageHeight - PIXELS_PER_PIXEL * 6);
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right
        this.g2d.fillRect(PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left

        // drawing the dark gray
        this.g2d.setColor(DARK_BORDER_COLOR);
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 4); // vertical right
        this.g2d.fillRect(PIXELS_PER_PIXEL * 3, imageHeight - PIXELS_PER_PIXEL * 3, imageWidth - PIXELS_PER_PIXEL * 6, PIXELS_PER_PIXEL * 2); // horizontal bottom
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 4, imageHeight - PIXELS_PER_PIXEL * 4, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // square bottom right

        // drawing the white border
        this.g2d.setColor(Color.WHITE);
        this.g2d.fillRect(PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 4); // vertical left
        this.g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, imageWidth - PIXELS_PER_PIXEL * 6, PIXELS_PER_PIXEL * 2); // horizontal top
        this.g2d.fillRect(PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // square top left

        this.g2d.setColor(Color.BLACK);
        // vertical black lines
        this.g2d.fillRect(0, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 5); // vertical left
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL, PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 5); // vertical right
        // horizontal black lines
        this.g2d.fillRect(PIXELS_PER_PIXEL * 2, 0, imageWidth - PIXELS_PER_PIXEL * 5, PIXELS_PER_PIXEL); // horizontal top
        this.g2d.fillRect(PIXELS_PER_PIXEL * 3, imageHeight - PIXELS_PER_PIXEL, imageWidth - PIXELS_PER_PIXEL * 5, PIXELS_PER_PIXEL); // horizontal bottom
        // black corners
        this.g2d.fillRect(PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top left
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right - upper
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // top right - lower
        this.g2d.fillRect(imageWidth - PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom right
        this.g2d.fillRect(PIXELS_PER_PIXEL, imageHeight - PIXELS_PER_PIXEL * 3, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left - upper
        this.g2d.fillRect(PIXELS_PER_PIXEL * 2, imageHeight - PIXELS_PER_PIXEL * 2, PIXELS_PER_PIXEL, PIXELS_PER_PIXEL); // bottom left - lower
    }

    private void drawTitle() {
        if (drawTitle) {
            this.g2d.setColor(DROP_SHADOW_COLOR);
            this.g2d.drawString(this.containerTitle, 8 * PIXELS_PER_PIXEL, this.coordinates.get(0).getY() - PIXELS_PER_PIXEL * 4);
        }
    }

    public void drawItems(String inventoryData) {
        InventoryStringParser parser = new InventoryStringParser(this.totalSlots);
        ArrayList<InventoryItem> items = parser.parse(inventoryData);

        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                if (items.get(i).getSlot()[0] == items.get(j).getSlot()[0]) {
                    items.remove(i);
                    i--;
                    break;
                }
            }
        }

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
            ImageCoordinates slot = this.coordinates.get(slots[index] - 1);
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
        this.drawBorder();
        this.drawSlots();
        this.drawTitle();
        this.drawItems(this.inventoryString);

        return new GeneratedObject(inventoryImage);
    }

    public static class Builder implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;
        private String containerTitle;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private String inventoryString;

        public MinecraftInventoryGenerator.Builder withRows(int rows) {
            this.rows = Range.between(1, MAX_ROWS_GENERATED).fit(rows);
            return this;
        }

        public MinecraftInventoryGenerator.Builder withSlotsPerRow(int slotsPerRow) {
            this.slotsPerRow = Range.between(1, MAX_COLUMNS_GENERATED).fit(slotsPerRow);
            return this;
        }

        public MinecraftInventoryGenerator.Builder withContainerTitle(String containerTitle) {
            this.containerTitle = containerTitle;
            return this;
        }

        public MinecraftInventoryGenerator.Builder drawBorder(boolean drawBorder) {
            this.drawBorder = drawBorder;
            return this;
        }

        public MinecraftInventoryGenerator.Builder drawBackground(boolean drawBackground) {
            this.drawBackground = drawBackground;
            return this;
        }

        public MinecraftInventoryGenerator.Builder withInventoryString(String inventoryString) {
            this.inventoryString = inventoryString;
            return this;
        }

        @Override
        public MinecraftInventoryGenerator build() {
            return new MinecraftInventoryGenerator(rows, slotsPerRow, containerTitle, inventoryString, drawBorder, drawBackground);
        }
    }
}
