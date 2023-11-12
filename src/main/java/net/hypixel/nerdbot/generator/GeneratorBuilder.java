package net.hypixel.nerdbot.generator;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.generator.Item;
import net.hypixel.nerdbot.util.generator.overlay.*;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.hypixel.nerdbot.generator.GeneratorStrings.FONTS_NOT_REGISTERED;
import static net.hypixel.nerdbot.generator.GeneratorStrings.INVALID_HEAD_URL;
import static net.hypixel.nerdbot.generator.GeneratorStrings.INVALID_RARITY;
import static net.hypixel.nerdbot.generator.GeneratorStrings.ITEM_RESOURCE_NOT_LOADED;
import static net.hypixel.nerdbot.generator.GeneratorStrings.MALFORMED_HEAD_URL;
import static net.hypixel.nerdbot.generator.GeneratorStrings.MALFORMED_PLAYER_PROFILE;
import static net.hypixel.nerdbot.generator.GeneratorStrings.PLAYER_NOT_FOUND;
import static net.hypixel.nerdbot.generator.GeneratorStrings.REQUEST_PLAYER_UUID_ERROR;
import static net.hypixel.nerdbot.generator.GeneratorStrings.UNKNOWN_EXTRA_DETAILS;
import static net.hypixel.nerdbot.generator.GeneratorStrings.stripString;

@Log4j2
public class GeneratorBuilder {

    public static final int IMAGE_HEIGHT = 512;
    public static final int IMAGE_WIDTH = 512;
    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");

    private final HashMap<String, Item> items;
    private boolean itemsInitialisedCorrectly = true;
    private BufferedImage itemSpriteSheet;

    public GeneratorBuilder() {
        this.items = new HashMap<>();

        // loading all sprites for Minecraft Items
        try (InputStream itemStackStream = GeneratorCommands.class.getResourceAsStream("/minecraft_assets/spritesheets/minecraft_texture_atlas.png")) {
            if (itemStackStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/spritesheets/minecraft_texture_atlas.png\"");
            }

            itemSpriteSheet = ImageIO.read(itemStackStream);
        } catch (IOException e) {
            log.error("Couldn't initialise the item stack file for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }

        // loading the items position in the sprite sheet
        try (InputStream itemStream = GeneratorCommands.class.getResourceAsStream("/minecraft_assets/spritesheets/atlas_coordinates.json")) {
            if (itemStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/spritesheets/atlas_coordinates.json\"");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(itemStream));
            StringBuilder results = new StringBuilder();
            String currentLine = reader.readLine();

            while (currentLine != null) {
                results.append(currentLine).append("\n");
                currentLine = reader.readLine();
            }

            Item[] itemsFound = NerdBotApp.GSON.fromJson(results.toString(), Item[].class);
            for (Item item : itemsFound) {
                if (items.containsKey(item.getName())) {
                    log.error(item.getName() + " seems to be duplicated in the items list. It will be replaced for now, but you should probably look into why this happened.");
                }

                items.put(item.getName(), item);
            }
        } catch (IOException e) {
            log.error("Couldn't initialise the items for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }

        // loading the overlays for some Minecraft Items
        try (InputStream overlayStream = GeneratorCommands.class.getResourceAsStream("/minecraft_assets/textures/overlays.png")) {
            if (overlayStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/overlays.png\"");
            }

            HashMap<String, Overlay> overlaysHashMap = new HashMap<>();
            BufferedImage overlayImage = ImageIO.read(overlayStream);

            // leather armor
            Color leatherArmorColor = new Color(160, 101, 63);
            Overlay leatherHelmet = new NormalOverlay("LEATHER_HELMET", overlayImage.getSubimage(112, 0, 16, 16), true, leatherArmorColor, MCColor.LEATHER_ARMOR_COLORS);
            overlaysHashMap.put(leatherHelmet.getName(), leatherHelmet);
            Overlay leatherChestplate = new NormalOverlay("LEATHER_CHESTPLATE", overlayImage.getSubimage(96, 0, 16, 16), true, leatherArmorColor, MCColor.LEATHER_ARMOR_COLORS);
            overlaysHashMap.put(leatherChestplate.getName(), leatherChestplate);
            Overlay leatherLeggings = new NormalOverlay("LEATHER_LEGGINGS", overlayImage.getSubimage(128, 0, 16, 16), true, leatherArmorColor, MCColor.LEATHER_ARMOR_COLORS);
            overlaysHashMap.put(leatherLeggings.getName(), leatherLeggings);
            Overlay leatherBoots = new NormalOverlay("LEATHER_BOOTS", overlayImage.getSubimage(80, 0, 16, 16), true, leatherArmorColor, MCColor.LEATHER_ARMOR_COLORS);
            overlaysHashMap.put(leatherBoots.getName(), leatherBoots);

            // armor trims
            int[] defaultTrimColors = new int[]{-2039584, -4144960, -6250336, -8355712, -10461088, -12566464, -14671840, -16777216};
            Overlay helmetTrim = new MappedOverlay("HELMET_TRIM", overlayImage.getSubimage(64, 0, 16, 16), false, defaultTrimColors, MCColor.ARMOR_TRIM_COLOR, MCColor.ARMOR_TRIM_BINDING);
            overlaysHashMap.put(helmetTrim.getName(), helmetTrim);
            Overlay chestplateTrim = new MappedOverlay("CHESTPLATE_TRIM", overlayImage.getSubimage(16, 0, 16, 16), false, defaultTrimColors, MCColor.ARMOR_TRIM_COLOR, MCColor.ARMOR_TRIM_BINDING);
            overlaysHashMap.put(chestplateTrim.getName(), chestplateTrim);
            Overlay leggingsTrim = new MappedOverlay("LEGGINGS_TRIM", overlayImage.getSubimage(144, 0, 16, 16), false, defaultTrimColors, MCColor.ARMOR_TRIM_COLOR, MCColor.ARMOR_TRIM_BINDING);
            overlaysHashMap.put(leggingsTrim.getName(), leggingsTrim);
            Overlay bootsTrim = new MappedOverlay("BOOTS_TRIM", overlayImage.getSubimage(0, 0, 16, 16), false, defaultTrimColors, MCColor.ARMOR_TRIM_COLOR, MCColor.ARMOR_TRIM_BINDING);
            overlaysHashMap.put(bootsTrim.getName(), bootsTrim);

            // other items
            Overlay fireworkCharge = new NormalOverlay("FIREWORK_STAR_OVERLAY", overlayImage.getSubimage(48, 0, 16, 16), true, new Color(255, 255, 255), MCColor.FIREWORK_COLORS);
            overlaysHashMap.put(fireworkCharge.getName(), fireworkCharge);
            Overlay potionOverlay = new NormalOverlay("POTION_OVERLAY", overlayImage.getSubimage(160, 0, 16, 16), true, new Color(55, 93, 198), MCColor.POTION_COLORS);
            overlaysHashMap.put(potionOverlay.getName(), potionOverlay);
            Overlay spawnEgg = new DualLayerOverlay("SPAWN_EGG", overlayImage.getSubimage(176, 0, 16, 16), true, new Color(255, 255, 255), new Color(255, 255, 255), MCColor.SPAWN_EGG_COLORS);
            overlaysHashMap.put(spawnEgg.getName(), spawnEgg);
            Overlay tippedArrow = new NormalOverlay("TIPPED_ARROW_HEAD", overlayImage.getSubimage(192, 0, 16, 16), true, new Color(255, 255, 255), MCColor.POTION_COLORS);
            overlaysHashMap.put(tippedArrow.getName(), tippedArrow);

            // enchant glint
            Overlay smallEnchantGlint = new EnchantGlintOverlay("ENCHANT_GLINT_SMALL", overlayImage.getSubimage(32, 0, 16, 16), false);
            Overlay largeEnchantGlint = new EnchantGlintOverlay("ENCHANT_GLINT_LARGE", overlayImage.getSubimage(0, 16, 512, 512), false);

            Item.setAvailableOverlays(overlaysHashMap);
            Item.setSmallEnchantGlint(smallEnchantGlint);
            Item.setLargeEnchantGlint(largeEnchantGlint);
        } catch (IOException e) {
            log.error("Couldn't initialise the overlays for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }
    }

    /**
     * Converts text into a Minecraft Item tooltip into a rendered image
     *
     * @param event          the GuildSlashEvent which the command is triggered from
     * @param name           the name of the item
     * @param rarity         the rarity of the item
     * @param itemLoreString the lore of the item
     * @param type           the type of the item
     * @param addEmptyLine   if there should be an extra line added between the lore and the final type line
     * @param alpha          the transparency of the generated image
     * @param padding        if there is any extra padding around the edges to prevent Discord from rounding the corners
     * @param maxLineLength  the maximum length before content overflows onto the next
     * @param isNormalItem   if the item should add an extra line between the title and first line
     *
     * @return a Minecraft item description
     */
    @Nullable
    public BufferedImage buildItem(GuildSlashEvent event, String name, String rarity, String itemLoreString, String type,
                                   Boolean addEmptyLine, Integer alpha, Integer padding, Integer maxLineLength, boolean isNormalItem, boolean isCentered) {
        // Checking that the fonts have been loaded correctly
        if (!MinecraftImage.isFontsRegistered()) {
            event.getHook().sendMessage(FONTS_NOT_REGISTERED).setEphemeral(true).queue();
            return null;
        }

        // verify rarity argument
        if (Arrays.stream(Rarity.VALUES).noneMatch(rarity1 -> rarity.equalsIgnoreCase(rarity1.name()))) {
            event.getHook().sendMessage(String.format(INVALID_RARITY, stripString(rarity))).setEphemeral(true).queue();
            return null;
        }

        StringBuilder itemLore = new StringBuilder(itemLoreString);

        // adds the item's name to the array list
        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        if (!name.equalsIgnoreCase("NONE")) { // allow user to pass NONE for the title
            String createTitle = "%%" + itemRarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%\\n";
            itemLore.insert(0, createTitle);
        }

        // writing the rarity if the rarity is not none
        if (itemRarity != Rarity.NONE) {
            // checks if there is a type for the item
            if (type == null || type.equalsIgnoreCase("none")) {
                type = "";
            }
            // checking if there is custom line break happening
            if (addEmptyLine == null || !addEmptyLine) {
                itemLore.append("\\n");
            }

            // adds the items type in the item lore
            String createRarity = "\\n%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        } else {
            itemLore.append("\\n");
        }

        itemLore = new StringBuilder(itemLore.toString().replace("§", "&"));

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(maxLineLength);
        colorParser.parseString(itemLore);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return null;
        }

        // alpha value validation
        alpha = Objects.requireNonNullElse(alpha, 255); // checks if the image transparency was set
        alpha = Math.min(255, Math.max(alpha, 0)); // ensure range between 0-254

        // padding value validation
        padding = Objects.requireNonNullElse(padding, 0);
        padding = Math.max(0, padding);

        return new MinecraftImage(
            colorParser.getParsedDescription(),
            MCColor.GRAY,
            colorParser.getEstimatedImageWidth() * 30,
            alpha,
            padding,
            isNormalItem,
            isCentered
        )
            .render()
            .getImage();
    }

    /**
     * Renders a Minecraft Head into an image
     *
     * @param event     the GuildSlashEvent which the command is triggered from
     * @param textureID the skin id/player name of the target skin
     *
     * @return a rendered Minecraft head
     */
    @Nullable
    public BufferedImage buildHead(GuildSlashEvent event, String textureID) {
        // checking if the skin is supposed to be for a player
        if (textureID.length() <= 16) {
            textureID = getPlayerHeadURL(event, textureID);
            if (textureID == null) {
                return null;
            }
        }

        // checking if there is the has added the full url to the texture ID
        Matcher textureMatcher = TEXTURE_URL.matcher(textureID);
        if (textureMatcher.matches()) {
            textureID = textureMatcher.group(1);
        }

        // converting the skin retrieved into a 3d head
        BufferedImage skin;
        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureID);
            skin = ImageIO.read(target);
        } catch (MalformedURLException e) {
            event.getHook().sendMessage(MALFORMED_HEAD_URL).setEphemeral(false).queue();
            return null;
        } catch (IOException e) {
            event.getHook().sendMessage(String.format(INVALID_HEAD_URL, stripString(textureID))).setEphemeral(false).queue();
            return null;
        }

        // registering the image into the cache
        return new MinecraftHead(skin).drawHead().getImage();
    }

    /**
     * Converts a player name into a skin id
     *
     * @param event      the GuildSlashEvent which the command is triggered from
     * @param playerName the name of the player
     *
     * @return the skin id for the player's skin
     */
    @Nullable
    private String getPlayerHeadURL(GuildSlashEvent event, String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(REQUEST_PLAYER_UUID_ERROR).queue();
            return null;
        }

        if (userUUID == null || userUUID.get("id") == null) {
            event.getHook().sendMessage(String.format(PLAYER_NOT_FOUND, playerName)).queue();
            return null;
        }

        JsonObject userProfile;
        try {
            userProfile = Util.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(REQUEST_PLAYER_UUID_ERROR).queue();
            return null;
        }

        if (userProfile == null || userProfile.get("properties") == null) {
            event.getHook().sendMessage(String.format(MALFORMED_PLAYER_PROFILE, stripString(playerName))).queue();
            return null;
        }

        String base64SkinData = userProfile.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
        return base64ToSkinURL(base64SkinData);
    }

    /**
     * Gets the Skin ID from a Base64 String
     *
     * @param base64SkinData Base64 Skin Data
     *
     * @return the skin id
     */
    public String base64ToSkinURL(String base64SkinData) {
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);
        return skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString().replace("http://textures.minecraft.net/texture/", "");
    }

    /**
     * Creates a rendered Minecraft Item image
     *
     * @param event        the event associated to the command
     * @param itemName     the name of the item
     * @param extraDetails any extra details about modifiers of the image
     *
     * @return a rendered minecraft image
     */
    @Nullable
    public BufferedImage buildItemStack(GuildSlashEvent event, String itemName, String extraDetails) {
        // checks that all the textures required to render the items were loaded correctly
        if (!itemsInitialisedCorrectly) {
            event.getHook().sendMessage(ITEM_RESOURCE_NOT_LOADED).queue();
            return null;
        }

        // finding the item that the user entered
        Item itemFound = items.get(itemName.toLowerCase());
        if (itemFound == null) {
            event.getHook().sendMessage(String.format(UNKNOWN_EXTRA_DETAILS, stripString(itemName), stripString(extraDetails))).queue();
            return null;
        }

        // copying the section of the item sprite sheet to a new image and applying any modifiers (color, enchant glint) to it
        int imageSize = itemFound.getSize();
        BufferedImage imagePortion = itemSpriteSheet.getSubimage(itemFound.getX(), itemFound.getY(), imageSize, imageSize);
        BufferedImage itemStack = new BufferedImage(imagePortion.getColorModel(), imagePortion.getRaster().createCompatibleWritableRaster(imageSize, imageSize), imagePortion.isAlphaPremultiplied(), null);
        imagePortion.copyData(itemStack.getRaster());
        itemFound.applyModifiers(itemStack, extraDetails);
        return itemStack;
    }

    /**
     * Creates a rendered generic Minecraft item image of a Skull or Item Stack
     *
     * @param event        the event associated to the command
     * @param itemName     the name of the item
     * @param extraDetails any extra details about modifiers of the image
     *
     * @return a rendered minecraft image
     */
    @Nullable
    public BufferedImage buildUnspecifiedItem(GuildSlashEvent event, String itemName, String extraDetails, boolean scaleImage) {
        BufferedImage itemImage;
        if (extraDetails == null) {
            extraDetails = "";
        }
        // checking if the user wanted to build something that isn't a skull
        if (itemName.equalsIgnoreCase("player_head") || itemName.equalsIgnoreCase("skull")) {
            if (extraDetails.length() > 0) {
                itemImage = buildHead(event, extraDetails);
            } else {
                itemImage = buildItemStack(event, "player_head", extraDetails);
            }
        } else {
            itemImage = buildItemStack(event, itemName, extraDetails);

            if (scaleImage && itemImage != null && itemImage.getWidth() <= 16) {
                Image copiedSection = itemImage.getScaledInstance(itemImage.getWidth() * 20, itemImage.getHeight() * 20, Image.SCALE_FAST);
                itemImage = new BufferedImage(itemImage.getWidth() * 20, itemImage.getHeight() * 20, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = itemImage.createGraphics();
                g2d.drawImage(copiedSection, 0, 0, null);
                g2d.dispose();
            }
        }

        return itemImage;
    }

    /**
     * Creates a rendered Minecraft Inventory with up to 9 different slots in a 3x3 grid
     *
     * @param event        the event associated to the command
     * @param recipeString the string which contains the recipe items
     *
     * @return a rendered minecraft crafting recipe
     */
    @Nullable
    public BufferedImage buildRecipe(GuildSlashEvent event, String recipeString, boolean renderBackground) {
        // checking that the resources were correctly loaded into memory
        if (!MinecraftInventory.resourcesRegistered()) {
            event.getHook().sendMessage(ITEM_RESOURCE_NOT_LOADED).queue();
            return null;
        }

        // creates a recipe parser to convert the string into different item slots
        RecipeParser parser = new RecipeParser();
        parser.parseRecipe(recipeString);
        if (!parser.isSuccessfullyParsed()) {
            event.getHook().sendMessage(parser.getErrorString()).queue();
            return null;
        }

        // iterates through each of the items and fetches the associated sprite/Minecraft head with its given attributes
        for (RecipeParser.RecipeItem item : parser.getRecipeData().values()) {
            // checking if the image was correctly found
            BufferedImage itemImage = buildUnspecifiedItem(event, item.getItemName(), item.getExtraDetails(), false);
            if (itemImage == null) {
                return null;
            }
            item.setImage(itemImage);
        }

        // renders the image
        MinecraftInventory inventory = new MinecraftInventory(parser.getRecipeData(), renderBackground).render();
        return inventory.getImage();
    }
}
