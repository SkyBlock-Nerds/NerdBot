package net.hypixel.nerdbot.generator;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.command.ItemGenCommands;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.generator.Item;
import net.hypixel.nerdbot.util.generator.Overlay;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static net.hypixel.nerdbot.generator.GeneratorStrings.*;
import static net.hypixel.nerdbot.generator.GeneratorStrings.stripString;

@Log4j2
public class GeneratorBuilder {
    private final HashMap<String, Item> items;
    private boolean itemsInitialisedCorrectly = true;
    private BufferedImage itemSpriteSheet;

    public GeneratorBuilder() {
        this.items = new HashMap<>();

        // loading all sprites for Minecraft Items
        try (InputStream itemStackStream = ItemGenCommands.class.getResourceAsStream("/Minecraft/item_stack_sprite_sheet.png")) {
            if (itemStackStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/item_stack_sprite_sheet.png\"");
            }

            itemSpriteSheet = ImageIO.read(itemStackStream);
        } catch (IOException e) {
            log.error("Couldn't initialise the item stack file for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }

        // loading the overlays for some Minecraft Items
        try (InputStream overlayStream = ItemGenCommands.class.getResourceAsStream("/Minecraft/overlays.png")) {
            if (overlayStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/overlays.png\"");
            }

            BufferedImage overlayImage = ImageIO.read(overlayStream);
            for (Overlay overlay : Overlay.values()) {
                overlay.setOverlayImage(overlayImage.getSubimage(overlay.getX(), overlay.getY(), 16, 16));
            }
        } catch (IOException e) {
            log.error("Couldn't initialise the overlays for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }

        // loading the items position in the sprite sheet
        try (InputStream itemStream = ItemGenCommands.class.getResourceAsStream("/Minecraft/items.json")) {
            if (itemStream == null) {
                throw new FileNotFoundException("Could not find find the file called \"/Minecraft/items.json\"");
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(itemStream));
            StringBuilder results = new StringBuilder();
            String currentLine = reader.readLine();

            while (currentLine != null) {
                results.append(currentLine).append("\n");
                currentLine = reader.readLine();
            }

            Item[] itemsFound = NerdBotApp.GSON.fromJson(results.toString(), Item[].class);
            StringBuilder oneParameterItems = new StringBuilder();
            StringBuilder twoParameterItems = new StringBuilder();
            for (Item item : itemsFound) {
                if (items.containsKey(item.getName())) {
                    log.error(item.getName() + " seems to be duplicated in the items list. It will be replaced for now, but you should probably look into why this happened.");
                }

                items.put(item.getName(), item);
                if (item.getOverlay() != null) {
                    (item.getOverlay().acceptedParameters() == 1 ? oneParameterItems : twoParameterItems).append(item.getName()).append(", ");
                }
            }

            oneParameterItems.delete(oneParameterItems.length() - 2, oneParameterItems.length());
            twoParameterItems.delete(twoParameterItems.length() - 2, twoParameterItems.length());
            GeneratorStrings.RECIPE_INFO_OTHER_INFORMATION = "**Ability to change one layer (One Hex Color Parameter)**\n" +
                    oneParameterItems + "\n\n**Ability to change both layers (Two Hex Color Parameters)**\n" + twoParameterItems;
        } catch (IOException e) {
            log.error("Couldn't initialise the items for ItemStack Generation");
            log.error(e.getMessage());
            itemsInitialisedCorrectly = false;
        }
    }

    /**
     * Converts text into a Minecraft Item tooltip into a rendered image
     *
     * @param event             the GuildSlashEvent which the command is triggered from
     * @param name              the name of the item
     * @param rarity            the rarity of the item
     * @param itemLoreString    the lore of the item
     * @param type              the type of the item
     * @param addEmptyLine      if there should be an extra line added between the lore and the final type line
     * @param alpha             the transparency of the generated image
     * @param padding           if there is any extra padding around the edges to prevent Discord from rounding the corners
     * @param maxLineLength     the maximum length before content overflows onto the next
     * @param isNormalItem      if the item should add an extra line between the title and first line
     *
     * @return                  a Minecraft item description
     */
    @Nullable
    public BufferedImage buildItem(GuildSlashEvent event, String name, String rarity, String itemLoreString, String type,
                                     Boolean addEmptyLine, Integer alpha, Integer padding, Integer maxLineLength, boolean isNormalItem) {
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

        maxLineLength = Objects.requireNonNullElse(maxLineLength, StringColorParser.MAX_STANDARD_LINE_LENGTH);
        maxLineLength = Math.min(StringColorParser.MAX_FINAL_LINE_LENGTH, Math.max(1, maxLineLength));
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

        MinecraftImage minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, maxLineLength * 25, alpha, padding, isNormalItem).render();

        Member member = event.getMember();
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), member.getId());
        if (discordUser == null) {
            log.info("Not updating last item generator activity date for " + member.getEffectiveName() + " (ID: " + member.getId() + ") since we cannot find a user!");
        } else {
            discordUser.getLastActivity().setLastItemGenUsage(System.currentTimeMillis());
            log.info("Updating last item generator activity date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
        }

        return minecraftImage.getImage();
    }

    /**
     * Renders a Minecraft Head into an image
     *
     * @param event           the GuildSlashEvent which the command is triggered from
     * @param textureID       the skin id/player name of the target skin
     * @param isPlayerName    if the provided textureID is a player name
     *
     * @return                a rendered Minecraft head
     */
    @Nullable
    public BufferedImage buildHead(GuildSlashEvent event, String textureID, Boolean isPlayerName) {
        // checking if the skin is supposed to be for a player
        if (isPlayerName != null && isPlayerName) {
            textureID = getPlayerHeadURL(event, textureID);
            if (textureID == null) {
                return null;
            }
        }

        // checking if there is the has added the full url to the texture ID
        if (textureID.contains("http://textures.minecraft.net/texture/")) {
            textureID = textureID.replace("http://textures.minecraft.net/texture/", "");
            event.getHook().sendMessage(HEAD_URL_REMINDER).setEphemeral(true).queue();
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
     * @param event         the GuildSlashEvent which the command is triggered from
     * @param playerName    the name of the player
     *
     * @return              the skin id for the player's skin
     */
    @Nullable
    private String getPlayerHeadURL(GuildSlashEvent event, String playerName) {
        playerName = stripString(playerName);

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(REQUEST_PLAYER_UUID_ERROR).queue();
            return null;
        }

        if (userUUID == null || userUUID.get("id") == null) {
            event.getHook().sendMessage(PLAYER_NOT_FOUND).queue();
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
     * @param base64SkinData    Base64 Skin Data
     * @return                  the skin id
     */
    public String base64ToSkinURL(String base64SkinData) {
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);
        return skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString().replace("http://textures.minecraft.net/texture/", "");
    }

    /**
     * Creates a rendered Minecraft Item image
     *
     * @param event         the event associated to the command
     * @param itemName      the name of the item
     * @param extraDetails  any extra details about modifiers of the image
     *
     * @return              a rendered minecraft image
     */
    @Nullable
    public BufferedImage buildItemStack(GuildSlashEvent event, String itemName, String[] extraDetails) {
        // checks that all the textures required to render the items were loaded correctly
        if (!itemsInitialisedCorrectly) {
            event.getHook().sendMessage(ITEM_RESOURCE_NOT_LOADED).queue();
            return null;
        }

        // finding the item that the user entered
        Item itemFound = items.get(itemName.toUpperCase());
        if (itemFound == null) {
            event.getHook().sendMessage(String.format(UNKNOWN_EXTRA_DETAILS, stripString(itemName), stripString(Arrays.toString(extraDetails)))).queue();
            return null;
        }

        // copying the section of the item sprite sheet to a new image and applying any modifiers (color, enchant glint) to it
        BufferedImage imagePortion = itemSpriteSheet.getSubimage(itemFound.getX(), itemFound.getY(), 16, 16);
        BufferedImage itemStack = new BufferedImage(imagePortion.getColorModel(), imagePortion.getRaster().createCompatibleWritableRaster(16, 16), imagePortion.isAlphaPremultiplied(), null);
        imagePortion.copyData(itemStack.getRaster());
        itemFound.applyModifiers(itemStack, extraDetails);
        return itemStack;
    }

    /**
     * Creates a rendered generic Minecraft item image of a Skull or Item Stack
     *
     * @param event         the event associated to the command
     * @param itemName      the name of the item
     * @param extraDetails  any extra details about modifiers of the image
     *
     * @return              a rendered minecraft image
     */
    @Nullable
    public BufferedImage buildUnspecifiedItem(GuildSlashEvent event, String itemName, String extraDetails) {
        BufferedImage itemImage;
        // checking if the user wanted to build something that isn't a skull
        if (!itemName.equalsIgnoreCase("skull")) {
            itemImage = buildItemStack(event, itemName, extraDetails.split(","));
        } else {
            int splitIndex = extraDetails.indexOf(",");

            // getting the skin id/player name and if it is a player name to build the head
            String textureID;
            boolean isPlayerHead;
            if (splitIndex == -1) {
                textureID = extraDetails;
                isPlayerHead = false;
            } else {
                textureID = extraDetails.substring(0, splitIndex);
                isPlayerHead = Boolean.parseBoolean(extraDetails.substring(splitIndex + 1));
            }
            itemImage = buildHead(event, textureID, isPlayerHead);
        }

        return itemImage;
    }

    /**
     * Creates a rendered Minecraft Inventory with up to 9 different slots in a 3x3 grid
     *
     * @param event             the event associated to the command
     * @param recipeString      the string which contains the recipe items
     *
     * @return                  a rendered minecraft crafting recipe
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
            BufferedImage itemImage = buildUnspecifiedItem(event, item.getItemName(), item.getExtraDetails());
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
