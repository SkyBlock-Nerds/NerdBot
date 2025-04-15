package net.hypixel.nerdbot.generator;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.generator.parser.RecipeParser;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import static net.hypixel.nerdbot.util.Util.initFont;

@Log4j2
public class MinecraftInventory {
    private static final boolean RESOURCES_INITIALISED;
    private static final Font MINECRAFT_FONT;
    private static final int PIXELS_PER_SLOT = 18;
    private static final int PIXELS_PER_PIXEL = 8;
    private static final int SLOT_DIMENSION = PIXELS_PER_SLOT * PIXELS_PER_PIXEL;
    private static final Color NORMAL_TEXT_COLOR = new Color(255, 255, 255);
    private static final Color DROP_SHADOW_COLOR = new Color(63, 63, 63);
    private static BufferedImage INVENTORY_IMAGE;

    static {
        // Register Minecraft Font
        MINECRAFT_FONT = initFont("/minecraft/assets/fonts/minecraft.otf", PIXELS_PER_PIXEL * PIXELS_PER_PIXEL);
        if (MINECRAFT_FONT != null) {
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(MINECRAFT_FONT);
        }

        // loading the inventory background into memory
        try (InputStream backgroundStream = GeneratorCommands.class.getResourceAsStream("/minecraft/assets/textures/inventory_background.png")) {
            if (backgroundStream == null) {
                throw new FileNotFoundException("Could not find the file called \"/Minecraft/inventory_background.png\"");
            }
            INVENTORY_IMAGE = ImageIO.read(backgroundStream);
        } catch (IOException exception) {
            log.error("Couldn't initialise the item stack file for ItemStack Generation", exception);
        }

        RESOURCES_INITIALISED = MINECRAFT_FONT != null && INVENTORY_IMAGE != null;
    }

    private final Graphics2D g2d;
    private final int horizontalSlots;
    private final int verticalSlots;
    private final boolean renderBackground;
    private final HashMap<Integer, RecipeParser.RecipeItem> recipe;
    @Getter
    private BufferedImage image;

    public MinecraftInventory(HashMap<Integer, RecipeParser.RecipeItem> recipe, boolean renderBackground) {
        this.horizontalSlots = 3;
        this.verticalSlots = 3;
        this.recipe = recipe;
        this.renderBackground = renderBackground;

        this.g2d = this.initG2D();
    }

    /**
     * Checks if the fonts have been registered correctly
     */
    public static boolean resourcesRegistered() {
        return RESOURCES_INITIALISED;
    }

    /**
     * Creates an image, then initializes a Graphics2D object from that image.
     *
     * @return G2D object
     */
    private Graphics2D initG2D() {
        this.image = new BufferedImage(INVENTORY_IMAGE.getWidth(), INVENTORY_IMAGE.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = this.image.createGraphics();
        g2d.setFont(MINECRAFT_FONT);
        return g2d;
    }

    /**
     * Draws the background onto the image
     */
    private void createBackground() {
        if (this.renderBackground) {
            g2d.drawImage(INVENTORY_IMAGE, 0, 0, null);
        }
    }

    /**
     * Draws the items and amounts onto the recipe
     */
    private void drawRecipeItems() {
        for (RecipeParser.RecipeItem item : this.recipe.values()) {
            if (item.getSlot() < 1 || item.getSlot() > 9)
                continue;

            // converts the index into an x and y coordinates bottom right hand corner
            int itemSlotIndex = item.getSlot() - 1;
            int x = (itemSlotIndex % horizontalSlots) * SLOT_DIMENSION;
            int y = (itemSlotIndex / verticalSlots) * SLOT_DIMENSION;

            // drawing the item to the slot
            BufferedImage itemToDraw = item.getImage();
            int offset = Math.abs(itemToDraw.getWidth() - itemToDraw.getHeight()) / 2;
            this.g2d.drawImage(itemToDraw, x + PIXELS_PER_PIXEL, y + PIXELS_PER_PIXEL,
                x + SLOT_DIMENSION - PIXELS_PER_PIXEL, y + SLOT_DIMENSION - PIXELS_PER_PIXEL,
                -offset, 0, itemToDraw.getWidth() + offset, itemToDraw.getHeight(), null);

            // checking if numbers should be written
            if (item.getAmount() == 1)
                continue;

            // writes the text and drop shadow in the bottom right
            int textX = x + SLOT_DIMENSION - PIXELS_PER_PIXEL;
            int textY = y + SLOT_DIMENSION - PIXELS_PER_PIXEL;

            // moving the cursor to be writing as right justified
            int startBounds = (int) MINECRAFT_FONT.getStringBounds(String.valueOf(item.getAmount()), this.g2d.getFontRenderContext()).getWidth();
            startBounds -= PIXELS_PER_PIXEL;

            this.g2d.setColor(DROP_SHADOW_COLOR);
            this.g2d.drawString(String.valueOf(item.getAmount()), textX - startBounds + PIXELS_PER_PIXEL, textY + PIXELS_PER_PIXEL);
            this.g2d.setColor(NORMAL_TEXT_COLOR);
            this.g2d.drawString(String.valueOf(item.getAmount()), textX - startBounds, textY);
        }
    }

    /**
     * Renders the Minecraft crafting recipe
     *
     * @return a minecraft crafting recipe image
     */
    public MinecraftInventory render() {
        this.createBackground();
        this.drawRecipeItems();
        g2d.dispose();
        return this;
    }
}
