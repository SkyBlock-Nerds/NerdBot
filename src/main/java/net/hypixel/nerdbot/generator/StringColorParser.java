package net.hypixel.nerdbot.generator;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Stat;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class StringColorParser {

    /**
     * Returns an array list that parses Minecraft Color Codes, newlines, and wrapping.
     *
     * @param description String to be parsed
     * @param event       GuildSlashEvent to return errors to.
     *
     * @return Parsed description with a max of 35 characters, excluding special codes.
     */
    @Nullable
    public static ArrayList<String> parseDescription(String description, GuildSlashEvent event) {
        return parseDescriptionHelper(description, event);
    }

    /**
     * Returns an array list that parses Minecraft Color Codes, newlines, and wrapping.
     *
     * @param description String to be parsed
     *
     * @return Parsed description with a max of 35 characters, excluding special codes. null if it cannot be parsed.
     */
    @Nullable
    public static ArrayList<String> parseDescription(String description) {
        return parseDescriptionHelper(description, null);
    }

    @Nullable
    private static ArrayList<String> parseDescriptionHelper(String description, GuildSlashEvent event) {
        ArrayList<String> parsed = new ArrayList<>();
        MCColor[] colors = MCColor.VALUES;

        StringBuilder currString = new StringBuilder();
        int lineLength = 0; // where we are in curr string
        int charIndex = 0;  // where we are in description
        int breakLoopCount = 0; // break if we are hanging due to a runtime error

        // Go through the entire description, break it apart to put into an arraylist for rendering
        while (description.length() > charIndex) {
            // Make sure we're not looping infinitely and just hanging the thread
            breakLoopCount++;
            if (breakLoopCount > description.length() * 2) {
                if (event != null) {
                    String debug = "length: " + description.length() + "\n" +
                            "charIndex: " + charIndex + "\n" +
                            "character failed on: " + description.charAt(charIndex) + "\n" +
                            "string: " + description + "\n" +
                            "If you see this debug, please ping a developer. Thanks!\n";
                    event.getHook().sendMessage(debug).setEphemeral(true).queue();
                }
                return null;
            }

            boolean noColorFlag = false;
            // This block checks colors, newline characters, soft-wrapping,
            // and changes the text depending on those checks.
            if (charIndex + 1 <= description.length()) {
                // Color parsing
                if (description.charAt(charIndex) == '%' && description.charAt(charIndex + 1) == '%') {
                    int endCharIndex = 0;
                    // If a parameter can be passed, put that here.
                    StringBuilder specialSubString = new StringBuilder();
                    boolean specialSubStringFlag = false;
                    int specialSubStringIndex = -1;
                    for (int i = charIndex + 2; i < charIndex + 100; i++) {
                        if (i + 1 >= description.length()) {
                            endCharIndex = -1;
                            break;
                        }

                        if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                            if (specialSubStringFlag) {
                                endCharIndex = specialSubStringIndex;
                            } else {
                                endCharIndex = i;
                            }
                            break;
                        }

                        if (specialSubStringFlag && description.charAt(i) != '%') {
                            specialSubString.append(description.charAt(i));
                        }

                        // Special case for a specialSubString
                        if (description.charAt(i) == ':') {
                            specialSubStringFlag = true;
                            specialSubStringIndex = i;
                            continue;
                        }

                        if (i == 99) {
                            endCharIndex = -1;
                            break;
                        }

                        if (i + 2 > description.length()) {
                            endCharIndex = -1;
                            break;
                        }
                    }

                    // If we can't find the end percents, just continue
                    if (endCharIndex != -1) {
                        // Move away from color code
                        charIndex += 2;
                        String getSpecialString = description.substring(charIndex, endCharIndex);

                        // True if we find a valid color, stat, or gemstone.
                        boolean foundColor = false;
                        for (MCColor color : colors) {
                            if (getSpecialString.equalsIgnoreCase(color.name())) {
                                foundColor = true;
                                currString.append("%%").append(color).append("%%");
                                break;
                            }
                        }

                        // redundant check so we don't call for stats without needing them
                        if (!foundColor) {
                            for (Stat stat : Stat.VALUES) {
                                if (getSpecialString.equalsIgnoreCase(stat.name())) {
                                    foundColor = true;
                                    if (specialSubStringFlag) {
                                        currString.append("%%").append(stat.getSecondaryColor()).append("%%");
                                        currString.append(specialSubString).append(" ");
                                    }
                                    currString.append("%%").append(stat.getColor()).append("%%");
                                    currString.append(stat.getId());
                                    currString.append("%%GRAY%% ");
                                    lineLength += stat.getId().length();
                                    break;
                                }
                            }
                        }

                        // redundant check so we don't call for gems without needing them
                        if (!foundColor) {
                            for (Gemstone gemstone : Gemstone.VALUES) {
                                if (getSpecialString.equalsIgnoreCase(gemstone.name())) {
                                    foundColor = true;
                                    currString.append("%%DARK_GRAY%%").append(gemstone.getId()).append("%%GRAY%% ");
                                    lineLength += 4;
                                }
                            }
                        }

                        if (getSpecialString.equalsIgnoreCase("bold")) {
                            currString.append("%%BOLD%%");
                            foundColor = true;
                        }

                        if (!foundColor) {
                            if (event != null) {
                                getSpecialString = getSpecialString.replaceAll("[^a-zA-Z0-9_ ]", "");
                                StringBuilder failed = new StringBuilder("You used an invalid code `" + getSpecialString + "`. Valid colors:\n");
                                for (MCColor color : colors) {
                                    failed.append(color).append(" ");
                                }
                                failed.append("BOLD");
                                failed.append("\nValid Stats:\n");
                                for (Stat stat : Stat.VALUES) {
                                    failed.append(stat).append(" ");
                                }
                                failed.append("\nValid Gems:\n");
                                for (Gemstone gemstone : Gemstone.VALUES) {
                                    failed.append(gemstone).append(" ");
                                }
                                event.getHook().sendMessage(failed.toString()).setEphemeral(true).queue();
                            }
                            return null;
                        }

                        // Move away from the color code
                        charIndex = endCharIndex + 2;
                        if (specialSubStringFlag) {
                            charIndex += specialSubString.length() + 1;
                            lineLength += specialSubString.length();
                        }
                        continue;
                    }
                    // if we can't find the endCharIndex, we just move on here and set a flag
                    noColorFlag = true;
                }

                // Shorthand Color Parsing
                if (description.charAt(charIndex) == '&' && description.charAt(charIndex + 1) != ' ') {
                    for (MCColor color : colors) {
                        if (color.getColorCode() == description.charAt(charIndex + 1)) {
                            currString.append("%%").append(color).append("%%");
                            break;
                        }
                    }

                    if ('l' == description.charAt(charIndex + 1)) {
                        currString.append("%%BOLD%%");
                    }
                    charIndex += 2;
                    continue;
                }

                // Newline parsing
                if (description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n') {
                    parsed.add(currString.toString());
                    currString.setLength(0);
                    lineLength = 0;
                    charIndex += 2;
                    continue;
                }

                // Softwrap parsing
                if (description.charAt(charIndex) == ' ') {
                    charIndex++;

                    int colorCheck = 36; // An extra buffer so we don't wrap colors
                    boolean newLineFlag = true;
                    for (int i = charIndex; i < charIndex + (colorCheck - lineLength); i++) {
                        if (i + 1 > description.length()) {
                            newLineFlag = false;
                            break;
                        }

                        if (description.charAt(i) == ' ') {
                            newLineFlag = false;
                            break;
                        }
                        if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                            colorCheck += 2;

                            // Let's see if there's a color here. We'll check if it's valid later.
                            for (int j = i + 2; j < description.length(); j++) {
                                if (j + 2 <= description.length() && description.charAt(j) == '%' && description.charAt(j + 1) == '%') {
                                    break;
                                }
                                colorCheck++;
                            }
                        }
                    }

                    if (newLineFlag) {
                        parsed.add(currString.toString());
                        currString.setLength(0);
                        lineLength = 0;
                    }
                    continue;
                }

                // EOL Parsing
                if (lineLength > 35) {
                    parsed.add(currString.toString());
                    currString.setLength(0);
                    lineLength = 0;
                    continue;
                }
            }

            // Find next break
            int findNextIndex = 0;
            boolean spaceBreak = false;
            for (int i = charIndex; i < description.length(); i++) {
                if (i + 1 >= description.length()) {
                    // Edge case for EOS
                    findNextIndex++;
                    break;
                }

                if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                    if (i + 2 >= description.length() || noColorFlag) {
                        // Edge case for EOS or if color has already been determined to not be present
                        findNextIndex++;
                    }
                    break;
                }

                if (description.charAt(i) == '\\' && description.charAt(i + 1) == 'n') {
                    break;
                }

                if (description.charAt(i) == '&' && description.charAt(i + 1) != ' ') {
                    break;
                }

                if (description.charAt(i) == ' ') {
                    spaceBreak = true;
                    break;
                }

                // If we've reached the EOL
                if (findNextIndex > (36 - lineLength)) {
                    break;
                }

                findNextIndex++;
            }

            // We're not at EOL yet, so let's write what we've got so far
            String subWriteString = description.substring(charIndex, charIndex + findNextIndex);
            currString.append(subWriteString).append(spaceBreak ? " " : ""); // if we need a space, put it in

            lineLength += findNextIndex;
            charIndex += findNextIndex;
        }

        // Make sure to save the last word written
        parsed.add(currString.toString());

        return parsed;
    }
}
