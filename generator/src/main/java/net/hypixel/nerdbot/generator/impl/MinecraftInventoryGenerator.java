package net.hypixel.nerdbot.generator.impl;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.ImageUtil;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.inventory.InventoryStringParser;
import net.hypixel.nerdbot.generator.spritesheet.OverlayLoader;
import net.hypixel.nerdbot.generator.spritesheet.Spritesheet;
import net.hypixel.nerdbot.generator.util.MinecraftFonts;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@ToString
public class MinecraftInventoryGenerator implements Generator {

    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static final Color INVENTORY_BACKGROUND = new Color(198, 198, 198);
    private static final Color BORDER_COLOR = new Color(198, 198, 198);
    private static final Color DARK_BORDER_COLOR = new Color(85, 85, 85);

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
    }

    private final int rows;
    private final int slotsPerRow;
    private final String containerTitle;
    private final boolean drawTitle;
    private final boolean drawBorder;
    private final boolean drawBackground;
    private final int totalSlots;
    private final String inventoryString;
    private final boolean animateGlint;

    private BufferedImage inventoryImage;
    private Graphics2D g2d;
    private int borderSize;
    private int titleHeight;
    private List<ImageCoordinates> slotCoordinates;
    private GeneratedObject generatedObject;

    public MinecraftInventoryGenerator(int rows, int slotsPerRow, String containerTitle, String inventoryString, boolean drawBorder, boolean drawBackground, boolean animateGlint) {
        this.rows = rows;
        this.slotsPerRow = slotsPerRow;
        this.containerTitle = containerTitle;
        this.inventoryString = inventoryString;
        this.drawTitle = containerTitle != null;
        this.drawBorder = drawBorder;
        this.drawBackground = drawBackground;
        this.totalSlots = rows * slotsPerRow;
        this.animateGlint = animateGlint;

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
        this.g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        this.g2d.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));
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
        if (inventoryString == null || inventoryString.isBlank()) {
            return;
        }

        InventoryStringParser parser = new InventoryStringParser(totalSlots);
        List<InventoryItem> items = resolveSlotConflicts(parser.parse(inventoryString));

        boolean hasAnimation = false;
        for (InventoryItem item : items) {
            processItem(item);
            if (item.getAnimationFrames() != null && !item.getAnimationFrames().isEmpty()) {
                hasAnimation = true;
            }
        }

        if (animateGlint && hasAnimation) {
            List<BufferedImage> frames = buildAnimationFrames(items);
            if (!frames.isEmpty()) {
                int frameDelay = determineFrameDelay(items);

                try {
                    byte[] gifData = ImageUtil.toGifBytes(frames, frameDelay, true);
                    this.inventoryImage = frames.getFirst();

                    if (this.g2d != null) {
                        this.g2d.dispose();
                    }

                    this.g2d = inventoryImage.createGraphics();
                    this.g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    this.g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    this.g2d.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));

                    this.generatedObject = new GeneratedObject(gifData, frames, frameDelay);

                    return;
                } catch (IOException e) {
                    log.error("Failed to encode animated inventory, falling back to static frame", e);
                    this.inventoryImage = frames.getFirst();
                }
            }
        }

        for (InventoryItem item : items) {
            drawItem(g2d, item);
        }
    }

    /**
     * Ensures each slot is filled by the last item referencing it within the inventory string.
     */
    private List<InventoryItem> resolveSlotConflicts(List<InventoryItem> parsedItems) {
        if (parsedItems == null || parsedItems.isEmpty()) {
            return Collections.emptyList();
        }

        boolean[] slotHasItem = new boolean[this.totalSlots + 1];
        ArrayList<InventoryItem> filteredItems = new ArrayList<>(parsedItems.size());

        for (int index = parsedItems.size() - 1; index >= 0; index--) {
            InventoryItem item = parsedItems.get(index);
            int[] slots = item.getSlot();
            int[] amounts = item.getAmount();

            if (slots == null || amounts == null || slots.length != amounts.length) {
                continue;
            }

            int keptCount = 0;
            for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
                int slot = slots[slotIndex];
                if (slot >= 1 && slot <= this.totalSlots && !slotHasItem[slot]) {
                    slotHasItem[slot] = true;
                    slots[keptCount] = slot;
                    amounts[keptCount] = amounts[slotIndex];
                    keptCount++;
                }
            }

            if (keptCount == 0) {
                continue;
            }

            if (keptCount != slots.length) {
                item.setSlot(Arrays.copyOf(slots, keptCount));
                item.setAmount(Arrays.copyOf(amounts, keptCount));
            }

            filteredItems.add(item);
        }

        Collections.reverse(filteredItems);
        return filteredItems;
    }

    private void processItem(InventoryItem item) {
        if (item.getItemName().contains("player_head")) {
            String skinValue = item.getExtraContent();
            if (skinValue != null && skinValue.contains(",")) {
                String[] tokens = skinValue.split(",");
                for (String token : tokens) {
                    token = token.trim();
                    if (token.toLowerCase().startsWith("skin=")) {
                        skinValue = token.substring(token.indexOf('=') + 1).trim();
                        break;
                    }
                }
            }
            if (skinValue != null && skinValue.toLowerCase().startsWith("skin=")) {
                skinValue = skinValue.substring(skinValue.indexOf('=') + 1).trim();
            }

            GeneratedObject playerHeadObject = new MinecraftPlayerHeadGenerator.Builder()
                .withSkin(skinValue)
                .build()
                .generate();
            item.setItemImage(playerHeadObject.getImage());
            item.setAnimationFrames(playerHeadObject.getAnimationFrames());
            item.setFrameDelayMs(playerHeadObject.getFrameDelayMs() > 0 ? playerHeadObject.getFrameDelayMs() : null);
        } else if (!item.getItemName().equalsIgnoreCase("null")) {
            // Parse extra content for color, armor trim, and other modifiers
            String extraContent = item.getExtraContent();
            String color = null;
            String armorTrim = null;
            boolean enchanted = false;
            boolean hoverEffect = false;
            List<String> remainingData = new ArrayList<>();

            if (extraContent != null && !extraContent.isBlank()) {
                String itemNameLower = item.getItemName().toLowerCase();
                boolean isArmor = itemNameLower.contains("helmet") || itemNameLower.contains("chestplate")
                    || itemNameLower.contains("leggings") || itemNameLower.contains("boots");

                OverlayLoader overlayLoader = OverlayLoader.getInstance();
                Set<String> trimMaterials = overlayLoader.getArmorTrimMaterials();
                Set<String> colorOptions = overlayLoader.getAllColorOptionNames();

                for (String token : extraContent.split(",")) {
                    String tokenTrimmed = token.trim();
                    String tokenLower = tokenTrimmed.toLowerCase();

                    if (tokenLower.equals("enchant") || tokenLower.equals("enchanted")) {
                        enchanted = true;
                    } else if (tokenLower.equals("hover")) {
                        hoverEffect = true;
                    } else if (isArmor && trimMaterials.contains(tokenLower)) {
                        armorTrim = tokenLower;
                    } else if (colorOptions.contains(tokenLower) || tokenLower.startsWith("#")) {
                        color = tokenLower;
                    } else {
                        remainingData.add(tokenTrimmed);
                    }
                }
            }

            MinecraftItemGenerator.Builder itemBuilder = new MinecraftItemGenerator.Builder()
                .withItem(item.getItemName())
                .withColor(color)
                .withArmorTrim(armorTrim)
                .isEnchanted(enchanted)
                .withHoverEffect(hoverEffect)
                .withData(remainingData.isEmpty() ? null : String.join(",", remainingData));

            if (item.getDurabilityPercent() != null) {
                itemBuilder.withDurability(item.getDurabilityPercent());
            }

            GeneratedObject generatedItem = itemBuilder.build().generate();
            item.setItemImage(generatedItem.getImage());
            item.setAnimationFrames(generatedItem.getAnimationFrames());
            item.setFrameDelayMs(generatedItem.getFrameDelayMs() > 0 ? generatedItem.getFrameDelayMs() : null);
        }
    }

    private void drawItem(Graphics2D target, InventoryItem item) {
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
                target.setColor(INVENTORY_BACKGROUND);
                target.fillRect(slotX + scaleFactor, slotY + scaleFactor,
                    slotSize - 2 * scaleFactor, slotSize - 2 * scaleFactor);
                continue;
            }

            // Calculate item position
            int itemPadding = (slotSize - itemSize) / 2;
            int itemX = slotX + itemPadding;
            int itemY = slotY + itemPadding;

            // Draw item image
            target.drawImage(itemImage, itemX, itemY, itemSize, itemSize, null);

            // Draw stack count if > 1
            if (amounts[index] > 1) {
                drawStackCount(target, amounts[index], slotX, slotY);
            }
        }
    }

    private void drawStackCount(Graphics2D target, int amount, int slotX, int slotY) {
        String amountText = String.valueOf(amount);

        Font originalFont = target.getFont();
        Font stackFont = MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8);
        target.setFont(stackFont);

        // Calculate text position (bottom-right of slot)
        int textWidth = target.getFontMetrics().stringWidth(amountText);
        int textX = slotX + slotSize - textWidth + 1;
        int textY = slotY + slotSize - scaleFactor + 1;

        // Draw text with drop shadow
        int shadowOffset = scaleFactor;
        target.setColor(DROP_SHADOW_COLOR);
        target.drawString(amountText, textX + shadowOffset - 1, textY + shadowOffset - 1);

        target.setColor(NORMAL_TEXT_COLOR);
        target.drawString(amountText, textX - 1, textY - 1);
        
        target.setFont(originalFont);
    }

    private List<BufferedImage> buildAnimationFrames(List<InventoryItem> items) {
        List<BufferedImage> frames = new ArrayList<>();
        int maxFrames = items.stream()
            .mapToInt(item -> item.getAnimationFrames() != null ? item.getAnimationFrames().size() : 1)
            .max()
            .orElse(0);

        if (maxFrames <= 1) {
            return frames;
        }

        for (int frameIndex = 0; frameIndex < maxFrames; frameIndex++) {
            BufferedImage frame = new BufferedImage(inventoryImage.getWidth(), inventoryImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D frameGraphics = frame.createGraphics();
            frameGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            frameGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            frameGraphics.setFont(MinecraftFonts.getFont(MinecraftFonts.REGULAR).deriveFont((float) scaleFactor * 8));
            frameGraphics.drawImage(inventoryImage, 0, 0, null);

            for (InventoryItem item : items) {
                BufferedImage original = item.getItemImage();
                if (item.getAnimationFrames() != null && !item.getAnimationFrames().isEmpty()) {
                    BufferedImage animatedFrame = item.getAnimationFrames().get(frameIndex % item.getAnimationFrames().size());
                    item.setItemImage(animatedFrame);
                }

                drawItem(frameGraphics, item);
                item.setItemImage(original);
            }

            frameGraphics.dispose();
            frames.add(frame);
        }

        return frames;
    }

    private int determineFrameDelay(List<InventoryItem> items) {
        return items.stream()
            .map(InventoryItem::getFrameDelayMs)
            .filter(delay -> delay != null && delay > 0)
            .findFirst()
            .orElse(33);
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

        if (generatedObject != null) {
            return generatedObject;
        }

        return new GeneratedObject(inventoryImage);
    }

    public static class Builder implements ClassBuilder<MinecraftInventoryGenerator> {
        private int rows;
        private int slotsPerRow;
        private String containerTitle;
        private boolean drawBorder = true;
        private boolean drawBackground = true;
        private String inventoryString;
        private boolean animateGlint;

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

        public Builder withAnimateGlint(boolean animateGlint) {
            this.animateGlint = animateGlint;
            return this;
        }

        @Override
        public MinecraftInventoryGenerator build() {
            if (rows <= 0 || slotsPerRow <= 0) {
                throw new IllegalArgumentException("rows and slotsPerRow must be positive");
            }
            return new MinecraftInventoryGenerator(rows, slotsPerRow, containerTitle, inventoryString, drawBorder, drawBackground, animateGlint);
        }
    }
}
