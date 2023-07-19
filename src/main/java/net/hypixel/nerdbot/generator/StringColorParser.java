package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Stat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StringColorParser {
    public static final int MAX_STANDARD_LINE_LENGTH = 38;
    public static final int MAX_FINAL_LINE_LENGTH = 80;

    private static final MCColor[] colors = MCColor.VALUES;
    private static final Stat[] stats = Stat.VALUES;
    private static final Gemstone[] gemstones = Gemstone.VALUES;

    // variables used to store the description
    private final List<List<ColoredString>> parsedDescription;
    private ArrayList<ColoredString> currentLine = new ArrayList<>();
    private ColoredString currentString;

    // variables for keeping track of line length and position
    private int charIndex;
    private int lineLength;
    private final int maxLineLength;

    private String errorString;
    private boolean successfullyParsed;

    public StringColorParser(Integer maxLength) {
        parsedDescription = new ArrayList<>();
        currentString = new ColoredString();

        charIndex = 0;
        lineLength = 0;
        successfullyParsed = false;

        maxLength = Objects.requireNonNullElse(maxLength, StringColorParser.MAX_STANDARD_LINE_LENGTH);
        maxLineLength = Math.min(StringColorParser.MAX_FINAL_LINE_LENGTH, Math.max(1, maxLength));
    }

    public List<List<ColoredString>> getParsedDescription() {
        return parsedDescription;
    }

    public boolean isSuccessfullyParsed() {
        return successfullyParsed;
    }

    public String getErrorString() {
        return errorString;
    }

    /**
     * Returns an array list that parses color coded, bolded and line wrapped lines.
     *
     * @param description String to be parsed
     */
    public void parseString(StringBuilder description) {
        int initialDescriptionLength = 2 * description.length();
        int breakLoopCount = 0;

        while (charIndex < description.length()) {
            // Make sure we're not looping infinitely and just hanging the thread
            breakLoopCount++;
            if (breakLoopCount > initialDescriptionLength) {
                errorString = "length: " + description.length() + "\n" +
                    "charIndex: " + charIndex + "\n" +
                    "character failed on: " + description.charAt(charIndex) + "\n" +
                    "string: " + description + "\n" +
                    "If you see this debug, please ping a developer. Thanks!\n";
                return;
            }

            if (charIndex + 1 < description.length()) {
                if (description.charAt(charIndex) == '%' && description.charAt(charIndex + 1) == '%') {
                    int closingIndex = description.indexOf("%%", charIndex + 1);
                    // check that there is a closing tag
                    if (closingIndex == -1) {
                        String surroundingError = description.substring(Math.max(charIndex - 10, 0), Math.min(charIndex + 10, description.length()));
                        this.errorString = String.format(GeneratorStrings.PERCENT_NOT_FOUND, GeneratorStrings.stripString(surroundingError));
                        return;
                    }
                    if (closingIndex <= charIndex + 2) {
                        this.errorString = GeneratorStrings.PERCENT_OUT_OF_RANGE;
                        return;
                    }

                    String selectedCommand = description.substring(charIndex + 2, closingIndex);
                    // checking if the command is a color code
                    MCColor mcColor = (MCColor) Util.findValue(colors, selectedCommand);
                    if (mcColor != null) {
                        // setting the correct option for the segment
                        switch (mcColor) {
                            case BOLD -> this.setBold(true);
                            case ITALIC -> this.setItalic(true);
                            case STRIKETHROUGH -> this.setStrikethrough(true);
                            case UNDERLINE -> this.setUnderlined(true);
                            case OBFUSCATED -> this.setObfuscated(true);
                            default -> this.setColor(mcColor);
                        }
                        charIndex = closingIndex + 2;
                        continue;
                    }

                    String currentColor = "&" + currentString.getCurrentColor().getColorCode() + (currentString.hasSpecialFormatting() ?
                                    (currentString.isBold() ? "&l" : "") + (currentString.isItalic() ? "&o" : "") + (currentString.isStrikethrough() ? "&m" : "") +
                                    (currentString.isUnderlined() ? "&n" : "") : "");

                    // checking if the command is a gemstone type
                    Gemstone gemstone = (Gemstone) Util.findValue(gemstones, selectedCommand);
                    if (gemstone != null) {
                        // replacing the selected space with the stat's text
                        String replacementText = "%%DARK_GRAY%%" + gemstone.getIcon() + currentColor;
                        description.replace(charIndex, closingIndex + 2, replacementText);
                        continue;
                    }

                    String extraData = "";
                    // checking if the command has extra special flags
                    if (selectedCommand.indexOf(':') != -1) {
                        int specialFlagIndex = selectedCommand.indexOf(':');
                        extraData = selectedCommand.substring(specialFlagIndex + 1);
                        selectedCommand = selectedCommand.substring(0, specialFlagIndex);
                    }
                    // checking if the command is supposed to be the icon
                    boolean isIcon = selectedCommand.indexOf('&') == 0;
                    if (isIcon) {
                        selectedCommand = selectedCommand.substring(1);
                    }

                    // checking if the command is a stat
                    Stat stat = (Stat) Util.findValue(stats, selectedCommand);
                    if (stat != null) {
                        // replacing the selected space with the stat's text
                        String replacementText = stat.getParsedStat(isIcon, extraData) + currentColor;
                        description.replace(charIndex, closingIndex + 2, replacementText);
                        continue;
                    }

                    // checking if the command is supposed to only trigger for the text inside it
                    MCColor tempStatColor = (MCColor) Util.findValue(colors, selectedCommand);
                    if (tempStatColor != null) {
                        // setting the correct color option for the segment
                        String replacementText = "&" + tempStatColor.getColorCode() + extraData + currentColor;
                        description.replace(charIndex, closingIndex + 2, replacementText);
                        continue;
                    }

                    // creating an error message showing the available stats, gemstones and color codes available
                    this.errorString = String.format(GeneratorStrings.INVALID_STAT_CODE, GeneratorStrings.stripString(selectedCommand));
                    return;
                }
                // checking if the user is using normal mc character codes
                else if (description.charAt(charIndex) == '&' && description.charAt(charIndex + 1) != ' ') {
                    char selectedCode = description.charAt(charIndex + 1);
                    // checking that the color code is real color
                    boolean foundMatchingColor = false;
                    for (MCColor mcColor : colors) {
                        if (mcColor.getColorCode() == selectedCode) {
                            foundMatchingColor = true;
                            // setting the correct option for the segment
                            switch (mcColor) {
                                case BOLD -> this.setBold(true);
                                case ITALIC -> this.setItalic(true);
                                case STRIKETHROUGH -> this.setStrikethrough(true);
                                case UNDERLINE -> this.setUnderlined(true);
                                case OBFUSCATED -> this.setObfuscated(true);
                                default -> this.setColor(mcColor);
                            }
                            charIndex += 2;
                            break;
                        }
                    }

                    // making sure that there was a color code found
                    if (foundMatchingColor) {
                        continue;
                    }

                    // creating error message for valid codes
                    this.errorString = GeneratorStrings.INVALID_MINECRAFT_COLOR_CODE;
                    return;
                }
                // checking if the current character is a new line
                else if ((description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n')) {
                    createNewLine();
                    charIndex += 2;

                    if (charIndex < description.length() && description.charAt(charIndex) == ' ') {
                        charIndex++;
                    }
                    continue;
                }
            }

            int nextSpace = description.indexOf(" ", charIndex + 1);
            int nextShortcut = description.indexOf("%%", charIndex + 1);
            int nextNewLine = description.indexOf("\\n", charIndex + 1);
            int nextPotentialMCColor = description.indexOf("&", charIndex + 1);

            // finding the nearest place that can be line split at (spaces, %% \n or &)
            int nearestSplit = nextSpace;
            for (int item : new int[]{nextShortcut, nextNewLine, nextPotentialMCColor}) {
                if (item != -1 && (nearestSplit == -1 || item < nearestSplit)) {
                    nearestSplit = item;
                }
            }
            // checks that there is a split that it can go to that isn't the end of the string
            if (nearestSplit == -1) {
                nearestSplit = description.length();
            }

            String currentSubstring = description.substring(charIndex, nearestSplit);
            if (lineLength + currentSubstring.length() >= maxLineLength) {
                // splitting the current string if it cannot fit onto a single line
                if (currentSubstring.length() >= maxLineLength) {
                    currentSubstring = currentSubstring.substring(0, maxLineLength);
                }

                createNewLine();
                // checking if the first character is a space
                if (currentSubstring.charAt(0) == ' ') {
                    currentSubstring = currentSubstring.substring(1);
                    charIndex++;
                }
            }

            // adds the current string to the array
            currentString.addString(currentSubstring);
            lineLength += currentSubstring.length();
            charIndex += currentSubstring.length();
        }

        // checks that the string is not empty before adding it.
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            parsedDescription.add(currentLine);
        }

        successfullyParsed = true;
    }

    /**
     * creates a new line within the arraylist, keeping the previous color
     */
    private void createNewLine() {
        currentLine.add(currentString);
        parsedDescription.add(currentLine);
        // creating a new line and segment
        currentLine = new ArrayList<>();
        currentString = new ColoredString(currentString);
        lineLength = 0;
    }

    /**
     * sets the color of the next segment
     *
     * @param color color to change to
     */
    private void setColor(MCColor color) {
        // checking if there is text on the current string before changing the color
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString();
        }

        currentString.setCurrentColor(color);
        currentString.setBold(false);
        currentString.setItalic(false);
        currentString.setStrikethrough(false);
        currentString.setUnderlined(false);
    }

    /**
     * sets if the next segment is bolded
     *
     * @param bold state of boldness to change to
     */
    private void setBold(boolean bold) {
        // checking if there is any text on the current string before changing it to bold
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setBold(bold);
    }

    /**
     * sets if the next segment has italic
     *
     * @param italic state of italics to change to
     */
    private void setItalic(boolean italic) {
        // checking if there is any text on the current string before changing it to italic
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setItalic(italic);
    }

    /**
     * sets if the next segment has strikethrough
     *
     * @param strikethrough state of strikethrough to change to
     */
    private void setStrikethrough(boolean strikethrough) {
        // checking if there is any text on the current string before changing it to italic
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setStrikethrough(strikethrough);
    }

    /**
     * sets if the next segment has underlined
     *
     * @param underline state of underlined to change to
     */
    private void setUnderlined(boolean underline) {
        // checking if there is any text on the current string before changing it to italic
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setUnderlined(underline);
    }

    /**
     * sets if the next segment has obfuscation
     *
     * @param obfuscated state of obfuscated to change to
     */
    private void setObfuscated(boolean obfuscated) {
        // checking if there is any text on the current string before changing it to obfuscated
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setStrikethrough(obfuscated);
    }
}
