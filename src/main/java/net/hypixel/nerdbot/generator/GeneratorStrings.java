package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import java.util.Arrays;

public class GeneratorStrings {
    // general messages
    public static final String FONTS_NOT_REGISTERED = "It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!";

    // itemgen item messages
    public static final String INVALID_RARITY;
    public static final String INVALID_STAT_CODE;
    public static final String INVALID_MINECRAFT_COLOR_CODE;
    public static final String PERCENT_NOT_FOUND = "It seems that you don't have a closing `%%` near `%s`.";
    public static final String PERCENT_OUT_OF_RANGE = "It seems that you are missing a starting/ending `%%` for a color code or stat.";

    // itemgen head messages
    public static final String HEAD_URL_REMINDER = "Hey, a small heads up - you don't need to include the full URL! Only the skin ID is required";
    public static final String MALFORMED_HEAD_URL = "Malformed... Url... Exception? (probably should contact one of the Bot Developers)";
    public static final String INVALID_HEAD_URL = "It seems that the URL you entered in doesn't link to anything...\nEntered URL: `http://textures.minecraft.net/texture/%s`";

    public static final String REQUEST_PLAYER_UUID_ERROR = "There was an error trying to send a request to get the UUID of this player...";
    public static final String PLAYER_NOT_FOUND = "It seems that there is no one with the name `%s`";
    public static final String MALFORMED_PLAYER_PROFILE = "There was a weird issue when trying to get the profile data for `%s`";

    // itemgen parse messages
    public static final String MISSING_NAME_VARIABLE = "It seems that you are missing a `Name` variable in your item's display tag";
    public static final String MISSING_LORE_VARIABLE = "It seems that you are missing a `Lore` variable in your item's display tag";
    public static final String ITEM_PARSE_JSON_FORMAT = "This JSON object seems to not be valid.\nIt should follow a similar format to...```json\n{\"Lore\": [\"lore array goes here\"], \"Name\": \"item name goes here\"}```";
    public static final String ITEM_PARSE_COMMAND = "Here is a /itemgen command if you want it!\n%s";

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
