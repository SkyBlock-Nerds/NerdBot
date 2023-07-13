package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.util.Arrays;

public class GeneratorStrings {
    // general messages
    public static final String FONTS_NOT_REGISTERED = "It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";
    public static final String TEXTURE_STREAM_NOT_REGISTERED = "It seems that the texture stream for recipe images couldn't be loaded correctly.";

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
    public static final String DESC_HEAD_ID = "The ID of the skin or the Player Name (set is_player_name to True if it is a player name)";
    public static final String DESC_IS_PLAYER_NAME = "If the skin ID given describes the player's name";
    public static final String DESC_HIDDEN = "If you only want the generated image visible to yourself";
    public static final String DESC_PARSE_ITEM = "Item JSON Display Data (in the form {\"Lore\": [...], \"Name\": \"\"}";
    public static final String DESC_INCLUDE_ITEM = "Create a full item generation instead of just the description";

    // item gen item messages
    public static final String INVALID_RARITY;
    public static final String INVALID_STAT_CODE;
    public static final String INVALID_MINECRAFT_COLOR_CODE;
    public static final String PERCENT_NOT_FOUND = "It seems that you don't have a closing `%%` near `%s`.";
    public static final String PERCENT_OUT_OF_RANGE = "It seems that you are missing a starting/ending `%%` for a color code or stat.";

    // item gen item help messages
    public static final String ITEM_BASIC_INFO = "This is a bot used to create custom items to be used in suggestions. You can use the bot with `/%1$s item`, `/1$s head`, and `/1$s full`.".formatted(COMMAND_PREFIX);
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
                        If you just want to get the icon for a specific stat, you can use `%%&PRISTINE%%` to automatically format it to the correct color, or retrieve it manually from the `/statsymbols` command.
                        Finally, you can move your text to a newline by typing `\\n`. If you don't want the extra line break at the end, set the `disable_rarity_linebreak` argument to True.
                        """;
    public static final String ITEM_OTHER_INFO = """
                        There is another command `/%1$s parse` which can be used to easily convert the display NBT Tag from a Minecraft item into a Generated Image. This display tag should be surrounded with curly brackets with a "Lore" (string array) and "Name" (string) attribute in them
                        You can also check out `/%1$s head_help` for more information about rendering items next to your creations!
                        Have fun making items! You can click the blue /%1$s command above anyone's image to see what command they're using to create their image. Thanks!
                        The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                        """.formatted(COMMAND_PREFIX);

    // item gen head messages
    public static final String HEAD_URL_REMINDER = "Hey, a small heads up - you don't need to include the full URL! Only the skin ID is required";
    public static final String MALFORMED_HEAD_URL = "Malformed... Url... Exception? (probably should contact one of the Bot Developers)";
    public static final String INVALID_HEAD_URL = "It seems that the URL you entered in doesn't link to anything...\nEntered URL: `http://textures.minecraft.net/texture/%s`";

    public static final String REQUEST_PLAYER_UUID_ERROR = "There was an error trying to send a request to get the UUID of this player...";
    public static final String PLAYER_NOT_FOUND = "It seems that there is no one with the name `%s`";
    public static final String MALFORMED_PLAYER_PROFILE = "There was a weird issue when trying to get the profile data for `%s`";

    // item gen head help messages
    public static final String HEAD_INFO_BASIC = "The command `/%s head` which will display a rendered Minecraft Head from a Skin (or player) you chose!".formatted(COMMAND_PREFIX);
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
    public static final String INVALID_ITEM_SKULL_DATA = "It seems that there is something wrong with the value given for the SkullOwner.Properties.textures. You should probably check that it is in a Json style (or ping a Bot Developer).";
    public static final String INVALID_BASE_64_SKIN_URL = "Either you are trying to be funny. Or there is something wrong with the Base64 string which this item's skull has.";
    public static final String ITEM_PARSE_JSON_FORMAT = "This JSON object seems to not be valid.\nYou should either copy it from in game, or follow a similar format to...```json\n{\"Lore\": [\"lore array goes here\"], \"Name\": \"item name goes here\"}```";
    public static final String ITEM_PARSE_COMMAND = "Here is a Nerd Bot generation command if you want it!\n```%s```";

    // item gen recipe messages
    public static final String MISSING_PARSED_RECIPE = "Did you even try to make a parsed recipe? Because I don't see the separator anywhere.";
    public static final String MISSING_FIELD_SEPARATOR = "This Recipe Item (`%s`) seems to not be valid\nIt should follow a similar format to... (each recipe item can be separated by %%)```json\n<item slot number>,<amount>,<item name>\n```";
    public static final String RECIPE_SLOT_NOT_INTEGER = "You have entered in a recipe slot (`%s`) that isn't a number within the recipe item `%s`";
    public static final String RECIPE_AMOUNT_NOT_INTEGER = "You have entered in a recipe amount (`%s`) that isn't a number within the recipe item `%s`";


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
