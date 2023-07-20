package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.util.Arrays;

public class GeneratorStrings {
    public static final String COMMAND_PREFIX = "gen";

    // general messages
    public static final String FONTS_NOT_REGISTERED = "It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";
    public static final String ITEM_RESOURCE_NOT_LOADED = "It seems that the texture stream for recipe images couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";

    // generator argument descriptions
    public static final String DESC_NAME = "The name of the item";
    public static final String DESC_RARITY = "The rarity of the item";
    public static final String DESC_ITEM_LORE = "The lore of the item";
    public static final String DESC_TEXT = "The text to display";
    public static final String DESC_TYPE = "The type of the item";
    public static final String DESC_EXTRA_ITEM_MODIFIERS = "Any modifiers which can be applied to the item (color, variants, isPlayerHead)";
    public static final String DESC_DISABLE_RARITY_LINEBREAK = "If you will deal with the line break before the item's rarity";
    public static final String DESC_ALPHA = "Sets the background transparency level (0 = transparent, 255 = opaque)";
    public static final String DESC_PADDING = "Sets the transparent padding around the image (0 = none, 1 = discord)";
    public static final String DESC_MAX_LINE_LENGTH = "Sets the maximum length for a line (1 - " + StringColorParser.MAX_FINAL_LINE_LENGTH + ") default " + StringColorParser.MAX_STANDARD_LINE_LENGTH;
    public static final String DESC_ITEM_NAME = "The name of the item you want to display";
    public static final String DESC_HEAD_ID = "The skin ID or player name (set is_player_name to True if it is a player's name)";
    public static final String DESC_IS_PLAYER_NAME = "If the skin ID is a player's username";
    public static final String DESC_HIDDEN = "If you only want the generated image visible to be yourself";
    public static final String DESC_PARSE_ITEM = "The items NBT Data from in game";
    public static final String DESC_INCLUDE_ITEM = "Include the item along with the parsed description";
    public static final String DESC_RENDER_INVENTORY = "If the Minecraft Inventory background should be drawn";
    public static final String DESC_RECIPE = "The recipe for crafting the item (/%s recipe_help for more info)".formatted(COMMAND_PREFIX);

    // item gen item messages
    public static final String INVALID_RARITY; // generated in static constructor
    public static final String INVALID_STAT_CODE; // generated in static constructor
    public static final String INVALID_MINECRAFT_COLOR_CODE; // generated in static constructor
    public static final String PERCENT_NOT_FOUND = "It seems that you don't have a closing `%%` near `%s`.";
    public static final String PERCENT_OUT_OF_RANGE = "It seems that you are missing a starting/ending `%%` for a color code or stat.";
    public static final String MISSING_FULL_GEN_ITEM = "I hope you know that the normal item generator works fine if you aren't going to put a recipe and/or Minecraft item.";

    // item gen item help messages
    public static final String ITEM_BASIC_INFO = "This is a bot used to create custom items to be used in suggestions. You can use the bot with `/%1$s item`, `/%1$s head`, and `/%1$s full`.".formatted(COMMAND_PREFIX);
    public static final String ITEM_INFO_ARGUMENTS = """
                        `name`: The name of the item. Defaults to the rarity color, unless the rarity is none.
                        `rarity`: Takes any SkyBlock rarity. Can be left as NONE.
                        `item_lore`: Parses a description, including color codes, bold, italics, and newlines.
                        `type`: The type of the item, such as a Sword or Wand. Can be left blank.
                        `disable_rarity_linebreak (true/false)`: To be used if you want to disable automatically adding the empty line between the item lore and rarity.
                        `alpha`: Sets the transparency of the background layer. 0 for transparent, 255 for opaque (default). 245 for overlay.
                        `padding`: Adds transparency around the entire image. Must be 0 (default) or higher.
                        `max_line_length`: Defines the maximum length that the line can be. Can be between 1 and 54.
                        """;
    public static final String ITEM_COLOR_CODES = """
                        The Item Generator bot also accepts color codes. You can use these with either manual Minecraft codes, such as `&1`, or Hypixel style color codes, such as `%%DARK_BLUE%%`.
                        You can use this same format for stats, such as `%%PRISTINE%%`. This format can also have numbers, where `%%PRISTINE:+1%%` will become "+1 ✧ Pristine".
                        If you just want to get the icon for a specific stat, you can use `%%&PRISTINE%%` to automatically format it to the correct color, or retrieve it manually from the `/stat_symbols` command.
                        Finally, you can move your text to a newline by typing `\\n`. If you don't want the extra line break at the end, set the `disable_rarity_linebreak` argument to True.
                        """;
    public static final String ITEM_OTHER_INFO = """
                        There is another command `/%1$s parse` which can be used to easily convert the NBT Tag from a Minecraft item into a Generated Image. You can directly copy it from in game using one of the Minecraft Mods.
                        You can also check out `/%1$s head_help` for more information about rendering items next to your creations!
                        Have fun making items! You can click the blue `/%1$s` command above anyone's image to see what command they're using to create their image. Thanks!
                        The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                        """.formatted(COMMAND_PREFIX);

    // item gen head messages
    public static final String HEAD_URL_REMINDER = "Hey, a small heads up - you don't need to include the full URL! Only the skin ID is required";
    public static final String MALFORMED_HEAD_URL = "It seems that there is something wrong with the URL that was entered on the developer side of it. Please contact one of the Bot Developers!";
    public static final String INVALID_HEAD_URL = "It seems that the URL you entered in doesn't link to anything...\nEntered URL: `http://textures.minecraft.net/texture/%s`";
    public static final String REQUEST_PLAYER_UUID_ERROR = "There was an error trying to send a request to get the UUID of this player...";
    public static final String PLAYER_NOT_FOUND = "It seems that there is no one with the name `%s`";
    public static final String MALFORMED_PLAYER_PROFILE = "There was a weird issue when trying to get the profile data for `%s`";

    // item gen head help messages
    public static final String HEAD_INFO_BASIC = "The command `/%s head` will display a rendered Minecraft Head from a skin or player of your choice!".formatted(COMMAND_PREFIX);
    public static final String HEAD_INFO_ARGUMENTS = """
                        `skin_id:` The skin ID or the player name of the person you wish to grab the skin from. (This is the string written after `http://textures.minecraft.net/texture/...`
                        `is_player_head:` set to True if the skin ID is a player's name
                        """;
    public static final String HEAD_INFO_OTHER_INFORMATION = """
                        If you are feeling extra spicy, you can combine these two elements by using the `/%s full` command with arguments mentioned previously.
                        The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                        """.formatted(COMMAND_PREFIX);

    // item gen parse messages
    public static final String MISSING_ITEM_NBT = "It seems that you haven't copied the item's NBT from in game, or is in another format. (Missing NBT Element: `%s`)";
    public static final String MULTIPLE_ITEM_SKULL_DATA = "It seems that there is either too many or not enough skull Base64 strings inside the SkullOwner.Properties.textures array. Should check that there is only one (if not, ping a Bot Developer)";
    public static final String INVALID_ITEM_SKULL_DATA = "It seems that there is something wrong with the value given for the SkullOwner.Properties.textures array. You should probably check that it is in a JSON style (or ping a Bot Developer).";
    public static final String INVALID_BASE_64_SKIN_URL = "It seems that you have entered an invalid Base64 skin url. Please double-check that it is right";
    public static final String ITEM_PARSE_JSON_FORMAT = "This JSON object seems to not be valid.\nYou should either copy it from in game, or follow a similar format to:```json\n{\n\tid: \"minecraft:stick\",\n\ttag: {\n\t\tdisplay: {\n\t\t\tLore: [\"§fJust your ordinary stick.\", \"\", \"§f§lCOMMON STICK\"],\n\t\t\tName: \"§fSticky\"\n\t\t}\n\t}\n}```";
    public static final String ITEM_PARSE_COMMAND = "Here is a Nerd Bot generation command if you want it!\n```%s```";

    // item gen recipe messages
    public static final String UNKNOWN_EXTRA_DETAILS = "Not exactly sure what `%s` (extra_details: `\"%s\"`). Maybe you didn't write its name correctly.";
    public static final String MISSING_PARSED_RECIPE = "Did you even try to make a parsed recipe? Because I don't see the separator anywhere.";
    public static final String MISSING_FIELD_SEPARATOR = "This Recipe Item (`%s`) seems to not be valid\nIt should follow a similar format to: ```json\n<item slot number>,<amount>,<item name>\n```\nEach recipe item can be separated by %%";
    public static final String RECIPE_SLOT_NOT_INTEGER = "You have entered in an item slot (`%s`) that isn't a number within the recipe item `%s`";
    public static final String RECIPE_SLOT_DUPLICATED = "It appears that you are trying to put multiple items into one slot (slot number: `%d`, duplicated recipe item: `%s`).";
    public static final String RECIPE_AMOUNT_NOT_INTEGER = "You have entered in an item amount (`%s`) that isn't a number within the recipe item `%s`";
    public static final String RECIPE_AMOUNT_NOT_IN_RANGE = "You have entered an item amount (`%s`) for the recipe item `%s` that is not in the range of 1-64. It would be funny if there were negative items required for a recipe though.";

    // item gen recipe help messages
    public static final String RECIPE_INFO_BASIC = "The command `/%s recipe` will display a rendered 3x3 square of Minecraft Items or Heads (limited to just items and no blocks right now)".formatted(COMMAND_PREFIX);
    public static final String RECIPE_INFO_ARGUMENTS = """
                               `recipe`: A string containing all of the recipe data for slot number, amount and the item name, separated by a `%%` between different slots.
                               
                               Each recipe item should be separated with a comma.
                               `slot_number`: The slot you want the item displayed in (`1-9` starting in the top left).
                               `amount`: The number of that item you want displayed.
                               `name_of_item`: The id of the item with spaces replaced with underscores.
                               `extra_details` (optional): Any extra attributes that the item may have.
           
                               This item can be displayed as enchanted by using `ENCHANT` inside the extra details parameter. Example: `5,26,DEAD_BUSH,ENCHANT` will display an Enchanted Dead Bush in the middle slot (5) with 26 of them.
                               
                               If you want to use a skull for one of the items, use `SKULL` as the name of the item, then put `<the skin id or player name>,<true/false if this is a player name>`in the extra details.
                               """;
    public static String RECIPE_INFO_OTHER_INFORMATION = "No items with overlays? They probably didn't get initialised correctly. Feel free to contact a Bot Developer about it"; // generated after the GeneratorBuilder has been constructed

    // stat symbols text
    public static final String STAT_SYMBOLS;

    static {
        MCColor[] colors = MCColor.VALUES;
        Stat[] stats = Stat.VALUES;
        Gemstone[] gemstones = Gemstone.VALUES;
        Rarity[] rarities = Rarity.VALUES;

        StringBuilder availableStatCodes = new StringBuilder(200);
        availableStatCodes.append("You used an invalid code `%s`.\nValid codes:\n");
        Arrays.stream(colors).forEach(color -> availableStatCodes.append(color).append(" "));
        availableStatCodes.append("\nValid Stats:\n");
        Arrays.stream(stats).forEach(stat -> availableStatCodes.append(stat).append(" "));
        availableStatCodes.append("\nValid Gems:\n");
        Arrays.stream(gemstones).forEach(gemstone -> availableStatCodes.append(gemstone).append(" "));
        INVALID_STAT_CODE = availableStatCodes.toString();

        StringBuilder availableMinecraftCodes = new StringBuilder(100);
        availableMinecraftCodes.append("You used an invalid character code `%c`.\nValid color codes include...\n");
        Arrays.stream(colors).forEach(color -> availableMinecraftCodes.append(color).append(": `").append(color.getColorCode()).append("`, "));
        availableMinecraftCodes.delete(availableMinecraftCodes.length() - 2, availableMinecraftCodes.length());
        INVALID_MINECRAFT_COLOR_CODE = availableMinecraftCodes.toString();

        StringBuilder availableRarities = new StringBuilder(150);
        availableRarities.append("You used an invalid rarity, `%s`.\nValid rarities:\n");
        Arrays.stream(rarities).forEachOrdered(rarity -> availableRarities.append(rarity.name()).append(" "));
        INVALID_RARITY = availableRarities.toString();

        StringBuilder statSymbolBuilder = new StringBuilder();
        statSymbolBuilder.append("Stats:\n```");
        for (Stat stat : Stat.VALUES) {
            if (stat.name().startsWith("ITEM_STAT")) {
                continue;
            }
            int length = 25 - stat.toString().length();
            statSymbolBuilder.append(stat).append(": ").append(" ".repeat(length)).append(stat.getDisplay()).append("\n");
        }

        statSymbolBuilder.append("\nOther Useful Icons\n");
        for (String icon : Stat.OTHER_ICONS) {
            statSymbolBuilder.append(icon).append(" ");
        }
        statSymbolBuilder.append("\n```");
        STAT_SYMBOLS = statSymbolBuilder.toString();
    }

    public static String stripString(String normalString) {
        return normalString.replaceAll("[^a-zA-Z0-9_ ]", "");
    }
}
