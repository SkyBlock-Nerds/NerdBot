package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.Icon;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.util.Arrays;
import java.util.stream.Collectors;

public class GeneratorStrings {
    public static final String COMMAND_PREFIX = "gen";

    // general messages
    public static final String FONTS_NOT_REGISTERED = "It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";
    public static final String ITEM_RESOURCE_NOT_LOADED = "It seems that the texture stream for recipe images couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";

    // generator argument descriptions
    public static final String DESC_ITEM_NAME = "The name of the item";
    public static final String DESC_RARITY = "The rarity of the item";
    public static final String DESC_ITEM_LORE = "The lore of the item";
    public static final String DESC_TEXT = "The text to display";
    public static final String DESC_TYPE = "The type of the item";
    public static final String DESC_EXTRA_ITEM_MODIFIERS = "Any modifiers which can be applied to the item (color, variants, is_player_name)";
    public static final String DESC_DISABLE_RARITY_LINEBREAK = "If you will deal with the line break before the item's rarity";
    public static final String DESC_ALPHA = "Sets the background transparency level (0 = transparent, 255 = opaque)";
    public static final String DESC_PADDING = "Sets the transparent padding around the image (0 = none, 1 = discord)";
    public static final String DESC_MAX_LINE_LENGTH = "Sets the maximum length for a line (1 - " + StringColorParser.MAX_FINAL_LINE_LENGTH + ") default " + StringColorParser.MAX_STANDARD_LINE_LENGTH;
    public static final String DESC_CENTERED = "Centers text to the middle of the image";
    public static final String DESC_ITEM_ID = "The name of the Minecraft item you want to display";
    public static final String DESC_HIDDEN = "If you only want the generated image visible to be yourself. (Deleted on client restart!)";
    public static final String DESC_PARSE_ITEM = "The items NBT Data from in game";
    public static final String DESC_INCLUDE_ITEM = "Include the item along with the parsed description";
    public static final String DESC_RENDER_INVENTORY = "If the Minecraft Inventory background should be drawn";
    public static final String DESC_RECIPE = "The recipe for crafting the item (see help recipe for more info)";

    // item gen general help messages
    public static final String GENERAL_INFO = """
                       Welcome to the Item Generation Bot!
                       Here you can use different commands to generate Minecraft related images which can help with designing content within Minecraft.
                       
                       You can click the blue `/%1$s` command above anyone's image to see what command they're using to create their image.
                       All of the item generator commands also have a `hidden` parameter. This can be set to `True` which will only send the image to you so that you can get some practice at making items, or design items in secret! If you do run commands as `hidden`, the image will disappear from chat whenever you restart your Discord client.
                       The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues, or ideas for improvements
                       
                       But most importantly, have fun making items!
                       """.formatted(COMMAND_PREFIX);
    public static final String GENERAL_HELP = """
                       Item Description Generation   ->   `/%1$s help item`
                       Item Text Generation          ->   `/%1$s help text`
                       Item Render Generation        ->   `/%1$s help display`
                       Recipe Generation             ->   `/%1$s help recipe`
                       Combined Item Generation      ->   `/%1$s help full`
                       Parse NBT to Command          ->   `/%1$s help parse`
                       """.formatted(COMMAND_PREFIX);

    // item gen item messages
    public static final String INVALID_RARITY; // generated in static constructor
    public static final String INVALID_STAT_CODE; // generated in static constructor
    public static final String INVALID_MINECRAFT_COLOR_CODE; // generated in static constructor
    public static final String PERCENT_NOT_FOUND = "It seems that you don't have a closing `%%` near `%s`.";
    public static final String PERCENT_OUT_OF_RANGE = "It seems that you are missing a starting/ending `%%` for a color code or stat.";

    // item gen item help messages
    public static final String ITEM_BASIC_INFO = "This is a bot used to create custom items to be used in suggestions. You can use the bot with `/%1$s item` and `/%1$s full`.".formatted(COMMAND_PREFIX);
    public static final String ITEM_INFO_ARGUMENTS = """
                        `item_name`: The name of the item. Defaults to the rarity color, unless the rarity is none.
                        `rarity`: Takes any SkyBlock rarity. Can be left as NONE.
                        `item_lore`: Parses a description, including color codes, bold, italics, and newlines.
                        """;
    public static final String ITEM_INFO_OPTIONAL_ARGUMENTS = """
                        `type`: The type of the item, such as a Sword or Wand. Can be left blank.
                        `disable_rarity_linebreak (true/false)`: To be used if you want to disable automatically adding the empty line between the item lore and rarity.
                        `alpha`: Sets the transparency of the background layer. 0 for transparent, 255 for opaque (default). 245 for overlay.
                        `padding`: Adds transparency around the entire image. Must be 0 (default) or higher.
                        `max_line_length`: Defines the maximum length that the line can be. Can be between 1 and 54.
                        """;
    public static final String ITEM_COLOR_CODES = """ 
                        The Item Generator bot also accepts color codes. You can use these with either manual Minecraft codes, such as `&1`, or Hypixel style color codes, such as `%%DARK_BLUE%%`. You can use `/%s help colors` to view all available colors.
                        You can use this same format for stats, such as `%%%%PRISTINE%%%%`. This format can also have numbers, where `%%%%PRISTINE:+1%%%%` will become "+1 ✧ Pristine".
                        If you just want to get the icon for a specific stat, you can use `%%%%&PRISTINE%%%%` to automatically format it to the correct color, or retrieve it manually from the `/%s help symbols` command.
                        Another coloring shortcut you can use, such as `%%%%GEM_TOPAZ%%%%`, adding the `[✧]` gemstone slot into the item.
                        """.formatted(COMMAND_PREFIX, COMMAND_PREFIX);

    public static final String ITEM_OTHER_INFO = """
                        The command will automatically soft-wrap text on your image so that you don't have to. You can change where this begins wrapping by using the `max_line_length` parameter. However, you can move your text to a newline by typing `\\n`.
                        There is also a line break placed at the before the rarity text. You can remove this line by setting the `disable_rarity_linebreak` argument to True.
                        """;

    public static final String ITEM_EXAMPLES = """
                       **Creating a Pancake Maker**
                       `/%1$s item item_name: Pancake Maker rarity: legendary item_lore: Creates pancakes! \\n\\n %%%%gold%%%%Item Ability: Batter Up! %%%%yellow%%%%%%%%bold%%%%RIGHT CLICK\\n%%%%gray%%%%Generates %%%%red%%%% 2 %%%%gray%%%%pancakes. When consumed, heal for %%%%health:+150%%%%%%%%gray%%%% and gain %%%%strength:20%%%% for 3 seconds.\\n%%%%dark_gray%%%%Mana Cost: %%%%dark_aqua%%%%75 hidden: true`
                       
                       **Creating a Aspect of the Pancake**
                       `/%1$s item item_name: Aspect of the Waffle rarity: EPIC item_lore: %%%%GEM_COMBAT%%%% %%%%GEM_COMBAT%%%%\\n&7Damage: &c+100\\n&7Strength: &c+100\\n &7Magic Find: &a+5\\n\\n%%%%ABILITY:Electro Waffle:RIGHT CLICK%%%%\\n&7Launch a &aWaffle &7at your enemies dealing &c50,000 &7damage and electrifiying them dealing &c1,000 &7damage per second.\\n%%%%MANA_COST:30%%%%\\n%%%%COOLDOWN:10s%%%%\\n\\n&8&oWait 'till the music begins.\\n\\n%%%%REFORGABLE%%%% type: SWORD disable_rarity_linebreak: true max_line_length: 37 hidden: true`
                       """.formatted(COMMAND_PREFIX);

    // item gen text help messages
    public static final String ITEM_TEXT_BASIC_INFO = "This is a command which can be used to generate Minecraft text similar to how it would appear in game! This command supports all the fancy tricks for changing colors, stats and icons!";
    public static final String ITEM_TEXT_INFO_ARGUMENTS = "`message`: The chat message you wish to display";
    public static final String ITEM_TEXT_INFO_OPTIONAL_ARGUMENTS = "`centered (true/false)`: If you want the text to be displayed in the center of the image";

    // item gen head messages
    public static final String MALFORMED_HEAD_URL = "It seems that there is something wrong with the URL that was entered on the developer side of it. Please contact one of the Bot Developers!";
    public static final String INVALID_HEAD_URL = "It seems that the URL you entered in doesn't link to anything...\nEntered URL: `http://textures.minecraft.net/texture/%s`";
    public static final String REQUEST_PLAYER_UUID_ERROR = "There was an error trying to send a request to get the UUID of this player...";
    public static final String PLAYER_NOT_FOUND = "It seems that there is no one with the name `%s`";
    public static final String MALFORMED_PLAYER_PROFILE = "There was a weird issue when trying to get the profile data for `%s`";

    // item gen display item help messages
    public static final String DISPLAY_INFO_BASIC = "The command `/%s display` will display a rendered Minecraft Item or Player Head!".formatted(COMMAND_PREFIX);
    public static final String DISPLAY_INFO_ARGUMENTS = "`item_id`: The ID of the Minecraft item that you want to display.";
    public static final String DISPLAY_INFO_OPTIONAL_ARGUMENTS = "`extra_modifiers`: Any extra modifiers for changing how the item displayed will look (i.e. Hex color for changing leather armor color, skull data)";
    public static final String DISPLAY_INFO_EXTRA_MODIFIERS = """
                       The `extra_modifiers` is where you put any attributes which will change the displayed item's appearance, each separated by a comma.
                       Below are some of the modifiers you can apply to items.
                       - Hex Code: A hex color code of the color (#000000)
                       - MC Color: Minecraft Dye Colors (cyan, magenta, lime)
                       - Armor Trim: Name of armor trim color (iron, gold, lapis)
                       - Potion Name: Color used by a MC potion (speed, instant_health)
                       - Mob Name: Name of a MC Mob (allay, zombie, ghast)""";
    public static final String DISPLAY_INFO_MODIFIERS = """
                       *Leather Armor*:
                       - Armor Color: Hex Code or MC Color
                       - Armor Trim: Armor Trim
                       *Armor*:
                       - Armor Trim: Armor Trim
                       *Firework Star*:
                       - Accent Color: Hex Code or MC Color
                       *Potion*:
                       - Liquid Color: Hex Code or Potion Name
                       *Tipped Arrow*:
                       - Arrow Color: Hex Color or Potion Name
                       *Spawn Egg*: (one group)
                       - Overall Egg Color: Mob Name
                       *Player Skull*
                       - Minecraft Skin ID or Player Name
                       """;
    public static final String DISPLAY_INFO_ENCHANT_GLINT = """
                       Items can be displayed as enchanted by using `enchanted` as the last modifier.
                       """;
    public static final String DISPLAY_ITEM_INFO_PLAYER_HEAD = """
                       As mentioned previously the `display_item` command can render Player Heads as well! Firstly set the `item_id` to `player_skull` so that the command knows you want to display a player head. Inside the `extra_modifiers` parameters, you would need to supply how you want the skin to be retrieved, which are as follows.

                       **By Player Name**
                       Simply type the Minecraft name of the player that you want the skin of.
                       Example: `%s` as the extra modifier will render your Minecraft skin as a head!

                       **By Skin ID**
                       This is used if you find a Minecraft Skin online somewhere. Simply copy the Minecraft Skin ID for the skin you want to render. If they give you a Skin Texture value, you can convert it from a Base64 string and grab that ID.
                       Example: `82ada1c7fcc8cf35defeb944a4f8ffa9a9d260560fc7f5f5826de8085435967c` as the extra modifier will render a Mana Flux Power Orb head!

                       *Note*: You do not need to include the `http://textures.minecraft.net/texture/` at the start.
                       """;

    // item gen parse messages
    public static final String MISSING_ITEM_NBT = "It seems that you haven't copied the item's NBT from in game, or is in another format. (Missing NBT Element: `%s`)";
    public static final String MULTIPLE_ITEM_SKULL_DATA = "It seems that there is either too many or not enough skull Base64 strings inside the SkullOwner.Properties.textures array. Should check that there is only one (if not, ping a Bot Developer)";
    public static final String INVALID_ITEM_SKULL_DATA = "It seems that there is something wrong with the value given for the SkullOwner.Properties.textures array. You should probably check that it is in a JSON style (or ping a Bot Developer).";
    public static final String INVALID_BASE_64_SKIN_URL = "It seems that you have entered an invalid Base64 skin url. Please double-check that it is right";
    public static final String ITEM_PARSE_JSON_FORMAT = "This JSON object seems to not be valid.\nYou should either copy it from in game, or follow a similar format to:```json\n{\n\tid: \"minecraft:stick\",\n\ttag: {\n\t\tdisplay: {\n\t\t\tLore: [\"§fJust your ordinary stick.\", \"\", \"§f§lCOMMON STICK\"],\n\t\t\tName: \"§fSticky\"\n\t\t}\n\t}\n}```";
    public static final String ITEM_PARSE_COMMAND = "Here is a Nerd Bot generation command if you want it!\n```%s```";

    // item gen parse help messages
    public static final String ITEM_PARSE_INFO = "The /%1$s command is extremely useful for being able to copy the NBT of items from in game and convert them into an Item Gen command so that you can modify them.\nUsing this can be extremely helpful for newer users as they will have a base to work from, or easily allows you to modify an existing item for rewording or upgrades.".formatted(COMMAND_PREFIX);
    public static final String ITEM_PARSE_ARGUMENTS = "`item_nbt`: The Minecraft NBT of the item (must be copied how you have it in your inventory)";
    public static final String ITEM_PARSE_OPTIONAL_ARGUMENTS = "`include_item (true/false)`: If you want to display the item along side the parsed image.";

    // item gen recipe messages
    public static final String UNKNOWN_EXTRA_DETAILS = "Not exactly sure what `%s` (extra_details: `\"%s\"`). Maybe you didn't write its name correctly or forgot to add %%%% separators.";
    public static final String MISSING_PARSED_RECIPE = "Did you even try to make a parsed recipe? Because I don't see the separator anywhere.";
    public static final String MISSING_FIELD_SEPARATOR = "This Recipe Item (`%s`) seems to not be valid\nIt should follow a similar format to: ```json\n<item_slot_number>,<amount>,<item_name>,<optional extra_details: hex color/enchant>\n```\nEach recipe item can be separated by %%%%";
    public static final String RECIPE_SLOT_NOT_INTEGER = "You have entered in an item slot (`%s`) that isn't a number within the recipe item `%s`";
    public static final String RECIPE_SLOT_DUPLICATED = "It appears that you are trying to put multiple items into one slot (slot number: `%d`, duplicated recipe item: `%s`).";
    public static final String RECIPE_AMOUNT_NOT_INTEGER = "You have entered in an item amount (`%s`) that isn't a number within the recipe item `%s`";
    public static final String RECIPE_AMOUNT_NOT_IN_RANGE = "You have entered an item amount (`%s`) for the recipe item `%s` that is not in the range of 1-64. It would be funny if there were negative items required for a recipe though.";

    // item gen recipe help messages
    public static final String RECIPE_INFO_BASIC = "The command `/%s recipe` will display a rendered 3x3 square of Minecraft Items or Heads".formatted(COMMAND_PREFIX);
    public static final String RECIPE_INFO_ARGUMENTS = """
                               `recipe`: A string containing all of the recipe data for slot number, amount and the item name, separated by a `%%%%` between different slots.
                               `render_background (true/false)`: If you want to display the Minecraft inventory in the background or leave it transparent.
                               
                               Each recipe item should be separated with `%%%%`.
                               `slot_number`: The slot you want the item displayed in (`1-9` starting in the top left).
                               `amount`: The number of that item you want displayed.
                               `item_id`: The id of the item with spaces replaced with underscores.
                               `extra_modifiers` (optional): Any extra modifiers that the item may have.
                               
                               The `item_id` and `extra_modifiers` work the same as displaying an item through the `/%1$s display` command. Run the `/%1$s help display` to get more information about it!
                               """.formatted(COMMAND_PREFIX);
    public static final String RECIPE_INFO_EXAMPLES = """
                               Overflux Power Orb Recipe Command
                               (Showcases enchanted items and player head fetched from a skin id)
                               `/%1$s recipe recipe: 1,32,gold_nugget,enchant %%%% 3,32,gold_nugget,enchant%%%% 7,32,gold_nugget,enchant%%%% 9,32,gold_nugget,enchant %%%% 2,1,quartz %%%% 4,8,redstone_block,enchant %%%% 6,8,redstone_block,enchant %%%% 8,8,redstone_block,enchant %%%% 5,1,PLAYER_HEAD,82ada1c7fcc8cf35defeb944a4f8ffa9a9d260560fc7f5f5826de8085435967c hidden: true`
                               
                               Goldor Chestplate Recipe Command
                               (Showcases items with overlays and how to customise them)
                               `/%1$s recipe recipe: 1,1,firework_star,pink %%%% 2,1,firework_star,pink %%%% 3,1,firework_star,pink %%%% 4,1,firework_star,pink %%%% 6,1,firework_star,pink %%%% 7,1,firework_star,pink %%%% 8,1,firework_star,pink %%%% 9,1,firework_star,pink %%%% 5,1,leather_chestplate,#e7413c hidden: true`
                               """.formatted(COMMAND_PREFIX);

    // item gen full error messages
    public static final String MISSING_FULL_GEN_ITEM = """
                        The parameters you have provided will not be enough to create an item generation.
                        For each type of generation you want to display, include the following parameters:
                        ```java
                        item generation         ->  item_name, rarity, item_lore
                        item render generation  ->  item_id, extra_modifiers
                        recipe generation       ->  recipe
                        ```
                        """;

    // item gen full help messages
    public static final String FULL_GEN_INFO = """
                        The `/%1$s full` command allows you to combine multiple renders together to create one full image showing all information about it.
                        
                        Follow the guide for `/%1$s help item` for more information about creating an item.
                        Follow the guide for `/%1$s help display` for more information about putting a item next to the item lore.
                        Follow the guide for `/%1$s help recipe` for more information about putting a recipe next to the item lore.
                        """.formatted(COMMAND_PREFIX);

    // stat symbols text
    public static final String STAT_SYMBOLS;

    static {
        MCColor[] colors = MCColor.VALUES;
        Stat[] stats = Stat.VALUES;
        Gemstone[] gemstones = Gemstone.VALUES;
        Rarity[] rarities = Rarity.VALUES;
        Icon[] icons = Icon.VALUES;

        INVALID_STAT_CODE = "You used an invalid option: `%s`" +
            "\n\n**Valid Colors:**\n" +
            Arrays.stream(colors).map(color1 -> color1 + " (`&" + color1.getColorCode() + "` or `%%%%" + color1 + "%%%%`)").collect(Collectors.joining(", ")) +
            "\n\n**Valid Stats:**\n" +
            Arrays.stream(stats).map(Stat::toString).collect(Collectors.joining(", ")) +
            "\n\n**Valid Icons:**\n" +
            Arrays.stream(icons).map(Icon::toString).collect(Collectors.joining(", ")) +
            "\n\n**Valid Gemstones:**\n" +
            Arrays.stream(gemstones).map(Gemstone::toString).collect(Collectors.joining(", "));

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
        for (Icon icon : Icon.VALUES) {
            int length = 25 - icon.toString().length();
            statSymbolBuilder.append(icon).append(": ").append(" ".repeat(length)).append(icon.getIcon()).append("\n");
        }
        statSymbolBuilder.append("\n```");
        STAT_SYMBOLS = statSymbolBuilder.toString();
    }

    public static String stripString(String normalString) {
        return normalString.replaceAll("[^a-zA-Z0-9_ ]", "");
    }
}
