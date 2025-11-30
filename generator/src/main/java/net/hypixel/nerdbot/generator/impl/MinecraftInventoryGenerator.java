package net.hypixel.nerdbot.generator.impl;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.inventory.InventoryStringParser;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.generator.util.FontUtils;
import net.hypixel.nerdbot.core.ImageUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@ToString
public class MinecraftInventoryGenerator implements Generator {

    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static final Color INVENTORY_BACKGROUND = new Color(198, 198, 198);
    private static final Color BORDER_COLOR = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);
    private static final Font MINECRAFT_FONT;

    private static final int SLOT_BORDER_THICKNESS = 1;
    private static final int SLOT_INNER_BORDER_OFFSET = 1;
    private static final int SLOT_INNER_BORDER_REDUCTION = 3;
    @Getter
    private static final int scaleFactor;
    private static final int slotSize;
    private static final int itemSize;
    private static BufferedImage slotTexture;

    static {
        // Detect item texture size automatically
        BufferedImage sampleItem = Spritesheet.getTexture("stone");
        if (sampleItem != null) {
            itemSize = sampleItem.getWidth() / 2;
            scaleFactor = itemSize / 16;
            slotSize = 18 * scaleFactor;
            log.info("Detected item texture size: {}x{}, factor: {}, slot size: {}", itemSize, itemSize, scaleFactor, slotSize);
        } else {
            // Fallback values if no item found
            itemSize = 128;
            scaleFactor = 8;
            slotSize = 144;
            log.info("Using fallback values: item size: {}, scale factor: {}, slot size: {}", itemSize, scaleFactor, slotSize);
        }

        try (InputStream slotStream = MinecraftInventoryGenerator.class.getResourceAsStream("/minecraft/assets/textures/slot.png")) {
            if (slotStream != null) {
                BufferedImage originalSlot = ImageIO.read(slotStream);
                slotTexture = ImageUtil.resizeImage(originalSlot, slotSize, slotSize, BufferedImage.TYPE_INT_ARGB);
            }
        } catch (IOException e) {
            log.error("Failed to load slot texture", e);
        }

        MINECRAFT_FONT = FontUtils.initFont("/minecraft/assets/fonts/Minecraft-Regular.otf", scaleFactor * 8);
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
    private int borderSize;
    private int titleHeight;
    private List<ImageCoordinates> slotCoordinates;

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString, boolean drawBorder, boolean drawBackground) {
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.containerTitle = containerTitle;
        this.inventoryString = inventoryString;
        this.drawTitle = containerTitle != null;
        this.drawBorder = drawBorder;
        this.drawBackground = drawBackground;
        this.totalSlots = rows * slotsPerRow;

        initializeImage();
    }

    private void initializeImage() {
        this.borderSize = drawBorder ? 7 * scaleFactor : 0;
        this.titleHeight = borderSize + (drawTitle ? 13 * scaleFactor : 0) - (drawBorder && drawTitle ? 3 * scaleFactor : 0);

        int imageWidth = (slotsPerRow * slotSize) + (borderSize * 2);
        int imageHeight = (rows * slotSize) + titleHeight + borderSize;

        this.inventoryImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        this.g2d = inventoryImage.createGraphics();
        this.g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        this.g2d.setFont(MINECRAFT_FONT);
    }

    private void drawInventoryBackground() {
        if (!drawBorder) return;

        drawMinecraftBorder();
    }

    private void drawMinecraftBorder() {
        int imageWidth = inventoryImage.getWidth();
        int imageHeight = inventoryImage.getHeight();

        // Drawing the background
        g2d.setColor(BORDER_COLOR);
        g2d.fillRect(scaleFactor * 3, scaleFactor * 3, imageWidth - scaleFactor * 6, imageHeight - scaleFactor * 6);
        g2d.fillRect(imageWidth - scaleFactor * 3, scaleFactor * 2, scaleFactor, scaleFactor); // top right
        g2d.fillRect(scaleFactor * 2, imageHeight - scaleFactor * 3, scaleFactor, scaleFactor); // bottom left

        // Drawing the dark gray shadow
        g2d.setColor(DARK_BORDER_COLOR);
        g2d.fillRect(imageWidth - scaleFactor * 3, scaleFactor * 3, scaleFactor * 2, imageHeight - scaleFactor * 4); // vertical right
        g2d.fillRect(scaleFactor * 3, imageHeight - scaleFactor * 3, imageWidth - scaleFactor * 6, scaleFactor * 2); // horizontal bottom
        g2d.fillRect(imageWidth - scaleFactor * 4, imageHeight - scaleFactor * 4, scaleFactor, scaleFactor); // square bottom right

        // Drawing the white highlight
        g2d.setColor(Color.WHITE);
        g2d.fillRect(scaleFactor, scaleFactor, scaleFactor * 2, imageHeight - scaleFactor * 4); // vertical left
        g2d.fillRect(scaleFactor * 3, scaleFactor, imageWidth - scaleFactor * 6, scaleFactor * 2); // horizontal top
        g2d.fillRect(scaleFactor * 3, scaleFactor * 3, scaleFactor, scaleFactor); // square top left

        g2d.setColor(Color.BLACK);
        // vertical black lines
        g2d.fillRect(0, scaleFactor * 2, scaleFactor, imageHeight - scaleFactor * 5); // vertical left
        g2d.fillRect(imageWidth - scaleFactor, scaleFactor * 3, scaleFactor, imageHeight - scaleFactor * 5); // vertical right
        // horizontal black lines
        g2d.fillRect(scaleFactor * 2, 0, imageWidth - scaleFactor * 5, scaleFactor); // horizontal top
        g2d.fillRect(scaleFactor * 3, imageHeight - scaleFactor, imageWidth - scaleFactor * 5, scaleFactor); // horizontal bottom
        // black corners
        g2d.fillRect(scaleFactor, scaleFactor, scaleFactor, scaleFactor); // top left
        g2d.fillRect(imageWidth - scaleFactor * 3, scaleFactor, scaleFactor, scaleFactor); // top right - upper
        g2d.fillRect(imageWidth - scaleFactor * 2, scaleFactor * 2, scaleFactor, scaleFactor); // top right - lower
        g2d.fillRect(imageWidth - scaleFactor * 2, imageHeight - scaleFactor * 2, scaleFactor, scaleFactor); // bottom right
        g2d.fillRect(scaleFactor, imageHeight - scaleFactor * 3, scaleFactor, scaleFactor); // bottom left - upper
        g2d.fillRect(scaleFactor * 2, imageHeight - scaleFactor * 2, scaleFactor, scaleFactor); // bottom left - lower
    }

    private void drawSlots() {
        slotCoordinates = new ArrayList<>();

        int startY = titleHeight;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < slotsPerRow; col++) {
                int x = borderSize + (col * slotSize);
                int y = startY + (row * slotSize);

                slotCoordinates.add(new ImageCoordinates(x, y));

                if (drawBackground) {
                    drawSlot(x, y);
                }
            }
        }
    }

    private void drawSlot(int x, int y) {
        if (slotTexture != null) {
            g2d.drawImage(slotTexture, x, y, null);
        } else {
            // Background
            g2d.setColor(INVENTORY_BACKGROUND);
            g2d.fillRect(x + scaleFactor, y + scaleFactor, slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);

            // Slot border
            g2d.setColor(DARK_BORDER_COLOR);
            g2d.drawRect(x, y, slotSize - SLOT_BORDER_THICKNESS, slotSize - SLOT_BORDER_THICKNESS);
            g2d.drawRect(x + SLOT_INNER_BORDER_OFFSET, y + SLOT_INNER_BORDER_OFFSET,
                slotSize - SLOT_INNER_BORDER_REDUCTION, slotSize - SLOT_INNER_BORDER_REDUCTION);

            // Highlight
            g2d.setColor(Color.WHITE);
            g2d.drawLine(x + slotSize - SLOT_BORDER_THICKNESS, y,
                x + slotSize - SLOT_BORDER_THICKNESS, y + slotSize - SLOT_BORDER_THICKNESS);
            g2d.drawLine(x, y + slotSize - SLOT_BORDER_THICKNESS,
                x + slotSize - SLOT_BORDER_THICKNESS, y + slotSize - SLOT_BORDER_THICKNESS);
        }
    }

    private void drawTitle() {
        if (!drawTitle || slotCoordinates == null || slotCoordinates.isEmpty()) {
            return;
        }

        int titleX = 8 * scaleFactor;
        int titleY = slotCoordinates.getFirst().getY() - scaleFactor * 4;

        g2d.setColor(DROP_SHADOW_COLOR);
        g2d.drawString(containerTitle, titleX, titleY);
    }

    private void drawItems() {
        if (inventoryString == null || inventoryString.isEmpty()) {
            return;
        }

        InventoryStringParser parser = new InventoryStringParser(totalSlots);
        ArrayList<InventoryItem> items = parser.parse(inventoryString);

        // Remove duplicate items by slot, keeping the last occurrence
        Map<String, InventoryItem> slotToItemMap = new LinkedHashMap<>();
        for (InventoryItem item : items) {
            if (item.getSlot() != null) {
                String slotKey = Arrays.toString(item.getSlot());
                slotToItemMap.put(slotKey, item);
            }
        }
        items.clear();
        items.addAll(slotToItemMap.values());

        for (InventoryItem item : items) {
            processAndDrawItem(item);
        }
    }

    private void processAndDrawItem(InventoryItem item) {
        if (item.getItemName().contains("player_head")) {
            MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(item.getExtraContent())
                .build();
            BufferedImage playerHeadImage = playerHeadGenerator.generate().getImage();
            item.setItemImage(playerHeadImage);
        } else if (!item.getItemName().equalsIgnoreCase("null")) {
            MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                .withItem(item.getItemName())
                .isEnchanted(item.getExtraContent() != null && item.getExtraContent().contains("enchant"))
                .withHoverEffect(item.getExtraContent() != null && item.getExtraContent().contains("hover"))
                .withData(item.getExtraContent());
            
            if (item.getDurabilityPercent() != null) {
                itemBuilder.withDurability(item.getDurabilityPercent());
            }
            
            BufferedImage generatedItem = itemBuilder.build().generate().getImage();
            item.setItemImage(generatedItem);
        }

        drawItem(item);
    }

    private void drawItem(InventoryItem item) {
        BufferedImage itemImage = item.getItemImage();
        int[] slots = item.getSlot();
        int[] amounts = item.getAmount();

        if (slots == null || amounts == null || slots.length != amounts.length) {
            return;
        }

        for (int index = 0; index < slots.length; index++) {
            if (slots[index] < 1 || slots[index] > slotCoordinates.size()) {
                continue;
            }

            ImageCoordinates slotCoord = slotCoordinates.get(slots[index] - 1);
            int slotX = slotCoord.getX();
            int slotY = slotCoord.getY();

            if (itemImage == null) {
                // Draw placeholder for null items
                g2d.setColor(INVENTORY_BACKGROUND);
                g2d.fillRect(slotX + scaleFactor, slotY + scaleFactor,
                    slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);
                continue;
            }

            // Calculate item position
            int itemPadding = (slotSize - itemSize) / 2;
            int itemX = slotX + itemPadding;
            int itemY = slotY + itemPadding;

            // Draw item image
            g2d.drawImage(itemImage, itemX, itemY, itemSize, itemSize, null);

            // Draw stack count if > 1
            if (amounts[index] > 1) {
                drawStackCount(amounts[index], slotX, slotY);
            }
        }
    }

    private void drawStackCount(int amount, int slotX, int slotY) {
        String amountText = String.valueOf(amount);

        Font originalFont = g2d.getFont();
        Font stackFont = MINECRAFT_FONT.deriveFont(Font.PLAIN, (float) scaleFactor * 8);
        g2d.setFont(stackFont);

        // Calculate text position (bottom-right of slot)
        int textWidth = g2d.getFontMetrics().stringWidth(amountText);
        int textX = slotX + slotSize - textWidth + 1;
        int textY = slotY + slotSize - scaleFactor + 1;

        // Draw text with drop shadow
        int shadowOffset = scaleFactor;
        g2d.setColor(DROP_SHADOW_COLOR);
        g2d.drawString(amountText, textX + shadowOffset - 1, textY + shadowOffset - 1);

        g2d.setColor(NORMAL_TEXT_COLOR);
        g2d.drawString(amountText, textX - 1, textY - 1);
        
        g2d.setFont(originalFont);
    }

    @Override
    public @NotNull GeneratedObject render() {
        log.debug("Rendering inventory ({})", this);

        drawInventoryBackground();
        drawSlots();
        drawTitle();
        drawItems();

        g2d.dispose();

        log.debug("Rendered inventory image (dimensions {}x{})", inventoryImage.getWidth(), inventoryImage.getHeight());
        return new GeneratedObject(inventoryImage);
    }

    public static class Builder implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;
        private String containerTitle;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private String inventoryString;

        public Builder withRows(int rows) {
            if (rows <= 0) {
                throw new IllegalArgumentException("rows must be positive");
            }
            this.rows = rows;
            return this;
        }

        public Builder withSlotsPerRow(int slotsPerRow) {
            if (slotsPerRow <= 0) {
                throw new IllegalArgumentException("slotsPerRow must be positive");
            }
            this.slotsPerRow = slotsPerRow;
            return this;
        }

        public Builder withContainerTitle(String containerTitle) {
            this.containerTitle = containerTitle;
            return this;
        }

        public Builder drawBorder(boolean drawBorder) {
            this.drawBorder = drawBorder;
            return this;
        }

        public Builder drawBackground(boolean drawBackground) {
            this.drawBackground = drawBackground;
            return this;
        }

        public Builder withInventoryString(String inventoryString) {
            this.inventoryString = inventoryString;
            return this;
        }

        @Override
        public MinecraftInventoryGenerator build() {
            if (rows <= 0 || slotsPerRow <= 0) {
                throw new IllegalArgumentException("rows and slotsPerRow must be positive");
            }
            return new MinecraftInventoryGenerator(rows, slotsPerRow, containerTitle, inventoryString, drawBorder, drawBackground);
        }
    }
}
