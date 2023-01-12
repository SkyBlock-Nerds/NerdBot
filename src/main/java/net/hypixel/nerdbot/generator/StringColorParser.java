package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Stat;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class StringColorParser {
    private static final int maxLineLength = 38;
    private final static MCColor[] colors = MCColor.VALUES;
    private final static Stat[] stats = Stat.VALUES;
    private final static Gemstone[] gemstones = Gemstone.VALUES;

    // variables used to store the description
    private final ArrayList<ArrayList<ColoredString>> parsedDescription;
    private ArrayList<ColoredString> currentLine = new ArrayList<>();
    private ColoredString currentString;

    // variables for keeping track of line length and position
    private int charIndex;
    private int lineLength;

    private String errorString;
    private boolean successfullyParsed;

    public StringColorParser() {
        parsedDescription = new ArrayList<>();
        currentString = new ColoredString();

        charIndex = 0;
        lineLength = 0;
        successfullyParsed = false;
    }

    public ArrayList<ArrayList<ColoredString>> getParsedDescription() {
        return parsedDescription;
    }

    public int getRequiredLines() {
        return parsedDescription.size();
    }

    public boolean didSuccessfullyParsed() {
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
                        String surrondingErrorSubstring = description.substring(Math.max(charIndex - 10, 0), Math.min(charIndex + 10, description.length()));
                        this.errorString = "It seems that you don't have a closing `%%` near `" + surrondingErrorSubstring + "`";
                        return;
                    }

                    String selectedCommand = description.substring(charIndex + 2, closingIndex);
                    // checking if the command is a bold code
                    if (selectedCommand.equalsIgnoreCase("bold")) {
                        this.setBold(true);
                        charIndex = closingIndex + 2;
                        continue;
                    }

                    // checking if the command is an italic code
                    if (selectedCommand.equalsIgnoreCase("italic")) {
                        this.setItalic(true);
                        charIndex = closingIndex + 2;
                        continue;
                    }

                    // checking if the command is a color code
                    MCColor color = (MCColor) findValue(colors, selectedCommand);
                    if (color != null) {
                        this.setColor(color);
                        charIndex = closingIndex + 2;
                        continue;
                    }

                    // checking if the command is a gemstone type
                    Gemstone gemstone = (Gemstone) findValue(gemstones, selectedCommand);
                    if (gemstone != null) {
                        // replacing the selected space with the stat's text
                        String replacementText = "%%DARK_GRAY%%" + gemstone.getId() + "%%GRAY%%";
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
                    // checking if the command is a stat
                    Stat stat = (Stat) findValue(stats, selectedCommand);
                    if (stat != null) {
                        // replacing the selected space with the stat's text
                        String replacementText = "%%" + stat.getColor() + "%%" + extraData + stat.getId() + "%%GRAY%%";
                        description.replace(charIndex, closingIndex + 2, replacementText);
                        continue;
                    }

                    // creating an error message showing the available stats, gemstones and color codes available
                    StringBuilder failedString = new StringBuilder("You used an invalid code `" + selectedCommand + "`. Valid colors:\n");
                    for (MCColor availableColors : colors) {
                        failedString.append(availableColors).append(" ");
                    }
                    failedString.append("BOLD");
                    failedString.append("\nValid Stats:\n");
                    for (Stat availableStats : Stat.VALUES) {
                        failedString.append(availableStats).append(" ");
                    }
                    failedString.append("\nValid Gems:\n");
                    for (Gemstone availableGemstones : Gemstone.VALUES) {
                        failedString.append(availableGemstones).append(" ");
                    }
                    this.errorString = failedString.toString();
                    return;
                }
                // checking if the user is using normal mc character codes
                else if (description.charAt(charIndex) == '&' && description.charAt(charIndex + 1) != ' ') {
                    // checking if the next character is for bolding
                    if (description.charAt(charIndex + 1) == 'l') {
                        this.setBold(true);
                        charIndex += 2;
                        continue;
                    }
                    // checking if the next character is for italics
                    if (description.charAt(charIndex + 1) == 'o') {
                        this.setItalic(true);
                        charIndex += 2;
                        continue;
                    }

                    char selectedCode = description.charAt(charIndex + 1);
                    // checking that the colour code is real color
                    boolean foundMatchingColor = false;
                    for (MCColor color : colors) {
                        if (color.getColorCode() == selectedCode) {
                            foundMatchingColor = true;
                            this.setColor(color);
                            charIndex += 2;
                            break;
                        }
                    }

                    // making sure that there was a color code found
                    if (foundMatchingColor) {
                        continue;
                    }

                    // creating error message for valid codes
                    StringBuilder failedString = new StringBuilder();
                    failedString.append("You used an invalid character code `").append(selectedCode).append("`. \nValid color codes include...");
                    for (MCColor color : colors) {
                        failedString.append(color).append(": ").append(color.getColorCode()).append(" ");
                    }
                    failedString.append("BOLD: l ITALIC: o");
                    this.errorString = failedString.toString();
                    return;
                }
                // checking if the current character is a new line
                else if ((description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n')) {
                    this.createNewLine();
                    charIndex += 2;

                    if (description.charAt(charIndex) == ' ')
                        charIndex++;
                    continue;
                }
            }

            int nextSpace = description.indexOf(" ", charIndex + 1);
            int nextShortcut = description.indexOf("%%", charIndex + 1);
            int nextNewLine = description.indexOf("\\n", charIndex + 1);
            int nextPotentialMCColor = description.indexOf("&", charIndex + 1);

            // finding the nearest place that can be line split at (spaces, %% \n or &)
            int nearestSplit = nextSpace;
            for (int item : new int[] {nextShortcut, nextNewLine, nextPotentialMCColor}) {
                if (item != -1 && (nearestSplit == -1 || item < nearestSplit))
                    nearestSplit = item;
            }
            // checks that there is a split that it can go to that isn't the end of the string
            if (nearestSplit == -1) {
                nearestSplit = description.length();
            }

            String currentSubstring = description.substring(charIndex, nearestSplit);
            if (lineLength + currentSubstring.length() > maxLineLength) {
                // splitting the current string if it cannot fit onto a single line
                if (currentSubstring.length() > maxLineLength)
                    currentSubstring = currentSubstring.substring(0, maxLineLength + 1);

                this.createNewLine();
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
     * creates a new line within the arraylist, keeping the previous colour
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
    }

    /**
     * sets if the next segment is bolded
     * @param isBold state of boldness to change to
     */
    private void setBold(boolean isBold) {
        // checking if there is any text on the current string before changing it to bold
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setBold(isBold);
    }

    /**
     * sets if the next segment has italic
     * @param isItalic state of italics to change to
     */
    private void setItalic(boolean isItalic) {
        // checking if there is any text on the current string before changing it to italic
        if (!currentString.isEmpty()) {
            currentLine.add(currentString);
            currentString = new ColoredString(currentString);
        }
        currentString.setItalic(isItalic);
    }

    /**
     * finds a matching value within a given set based on it's name
     * @param enumSet an array to search for the enum in
     * @param match the value to find in the array
     * @return returns the enum item or null if not found
     */
    @Nullable
    private static Enum<?> findValue(Enum<?>[] enumSet, String match) {
        for (Enum<?> enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name()))
                return enumItem;
        }

        return null;
    }
}
