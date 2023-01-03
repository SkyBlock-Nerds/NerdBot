package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.skyblock.Gemstone;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ItemGenCommand extends ApplicationCommand {

    @JDASlashCommand(name = "itemgen", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void askForInfo(
            GuildSlashEvent event,
            @AppOption(description = "The name of the item") String name,
            @AppOption(description = "The rarity of the item", autocomplete = "rarities") String rarity,
            @AppOption(description = "The description of the item") String description,
            @AppOption(description = "The type of the item") String type
    ) throws IOException {
        String senderChannelId = event.getChannel().getId();
        String itemGenChannelId = NerdBotApp.getBot().getConfig().getItemGenChannel();

        event.deferReply(false).queue();

        if (itemGenChannelId == null) {
            event.getHook().sendMessage("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return;
        }

        if (!senderChannelId.equals(itemGenChannelId)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannelId);
            if (channel == null) {
                event.getHook().sendMessage("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return;
            }
            event.getHook().sendMessage("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return;
        }

        //verify rarity argument
        String finalRarity = rarity;
        if (Arrays.stream(Rarity.values()).noneMatch(rarity1 -> finalRarity.equalsIgnoreCase(rarity1.name()))) {
            rarity = rarity.replaceAll("[^a-zA-Z0-9_ ]", "");
            StringBuilder failedRarity = new StringBuilder("You used an invalid rarity, `" + rarity + "`. Valid rarities:\n");
            Arrays.stream(Rarity.values()).forEachOrdered(rarity1 -> failedRarity.append(rarity1.name()).append(" "));
            event.getHook().sendMessage(failedRarity.toString()).setEphemeral(true).queue();
            return;
        }

        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        ArrayList<String> parsedDescription = parseDescription(description, event);
        if (parsedDescription == null || parsedDescription.isEmpty()) {
            event.getHook().sendMessage("Please enter a valid description for the item!").setEphemeral(true).queue();
            return;
        }

        parsedDescription.add(0, name);
        //Guess the height for our image. If there is no rarity, strip 2 lines of space from it.
        int heightEstimate = (((itemRarity == Rarity.NONE ? 1 : 3) + parsedDescription.size()) * 23) - 5;
        BufferedImage image = new BufferedImage(500, heightEstimate, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(41, 5, 96));
        g2d.drawRect(1, 1, 497, heightEstimate - 3);
        g2d.drawRect(2, 2, 497, heightEstimate - 3);
        
        Font minecraftFont;
        Font minecraftBold;
        try {
            InputStream minecraftFontStream = ItemGenCommand.class.getResourceAsStream("/Minecraft/minecraft.ttf");
            InputStream boldFontStream = ItemGenCommand.class.getResourceAsStream("/Minecraft/3_Minecraft-Bold.otf");
            if (minecraftFontStream == null || boldFontStream == null) {
                throw new NullPointerException();
            }
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, minecraftFontStream).deriveFont(16f);
            minecraftBold = Font.createFont(Font.TRUETYPE_FONT, boldFontStream).deriveFont(22f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(minecraftFont);
            ge.registerFont(minecraftBold);
            g2d.setFont(minecraftFont);
        } catch (IOException | FontFormatException | NullPointerException e) {
            e.printStackTrace();
            event.getHook().sendMessage("Something went wrong with creating the font. Try again later!").setEphemeral(true).queue();
            return;
        }

        //Let's generate and place our text
        int locationY = 25;
        int locationX = 10;

        //Go through our ArrayList, print each string on a new line
        boolean boldFlag = false; //True if the text is bold
        boolean titleFlag = true; //True if we're printing the title
        MCColor currentColor = itemRarity.getRarityColor(); //Start by printing the title in its color

        for (String line : parsedDescription) {
            locationX = 10;

            //Let's iterate through each character in our line, looking for colors
            StringBuilder subWord = new StringBuilder();
            for(int colorStartIndex = 0; colorStartIndex < line.length(); colorStartIndex++) {
                //Check for colors
                if ((colorStartIndex + 2 < line.length()) && (line.charAt(colorStartIndex) == '%') && (line.charAt(colorStartIndex + 1) == '%')) {
                    int colorEndIndex = -1;

                    //Get index of where color code ends.
                    for(int j = colorStartIndex + 1; j < line.length() - 2; j++) {
                        if (line.charAt(j + 1) == '%' && line.charAt(j + 2) == '%') {
                            colorEndIndex = j;
                            break;
                        }
                    }

                    if (colorEndIndex != -1) {
                        //We've previously verified that this is a good color, so let's trust it
                        g2d.setColor(currentColor.getBackgroundColor());
                        g2d.drawString(subWord.toString(), locationX + 2, locationY + 2);
                        g2d.setColor(currentColor.getColor());
                        g2d.drawString(subWord.toString(), locationX, locationY);

                        locationX += minecraftFont.getStringBounds(subWord.toString(), g2d.getFontRenderContext()).getWidth();
                        subWord.setLength(0);

                        String foundColor = line.substring(colorStartIndex + 2, colorEndIndex + 1);

                        if (foundColor.equalsIgnoreCase("bold")) {
                            g2d.setColor(currentColor.getBackgroundColor());
                            g2d.drawString(subWord.toString(), locationX + 2, locationY + 2);

                            g2d.setColor(currentColor.getColor());
                            g2d.drawString(subWord.toString(), locationX, locationY);

                            locationX += minecraftFont.getStringBounds(subWord.toString(), g2d.getFontRenderContext()).getWidth();
                            subWord.setLength(0);
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftBold);
                            boldFlag = true;
                        } else {
                            for (MCColor color : MCColor.values()) {
                                if (foundColor.equalsIgnoreCase(color.toString())) {
                                    currentColor = color;
                                }
                            }
                            g2d.setColor(currentColor.getColor());
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftFont);
                            boldFlag = false;
                        }
                    }
                } else if (!minecraftFont.canDisplay(line.charAt(colorStartIndex))) {
                    //We need to draw this character special, so let's get rid of our old word.
                    g2d.setColor(currentColor.getBackgroundColor());
                    g2d.drawString(subWord.toString(), locationX + 2, locationY + 2);
                    g2d.setColor(currentColor.getColor());
                    g2d.drawString(subWord.toString(), locationX, locationY);

                    if (boldFlag) {
                        locationX += minecraftBold.getStringBounds(subWord.toString(), g2d.getFontRenderContext()).getWidth();
                    } else {
                        locationX += minecraftFont.getStringBounds(subWord.toString(), g2d.getFontRenderContext()).getWidth();
                    }
                    subWord.setLength(0);

                    //Let's try to render the character in a normal font, and then return to the minecraft font.
                    Font sansSerif = new Font("SansSerif", Font.PLAIN, 20);
                    g2d.setFont(sansSerif);
                    subWord.append(line.charAt(colorStartIndex));

                    g2d.setColor(currentColor.getBackgroundColor());
                    g2d.drawString(subWord.toString(), locationX + 2, locationY + 2);

                    g2d.setColor(currentColor.getColor());
                    g2d.drawString(subWord.toString(), locationX, locationY);

                    locationX += sansSerif.getStringBounds(subWord.toString(), g2d.getFontRenderContext()).getWidth();
                    subWord.setLength(0);
                    g2d.setFont(boldFlag ? minecraftBold : minecraftFont);
                } else {
                    //We do this to prevent monospace bullshit
                    subWord.append(line.charAt(colorStartIndex));
                }
            }

            g2d.setColor(currentColor.getBackgroundColor());
            g2d.drawString(subWord.toString(), locationX + 2, locationY + 2);
            g2d.setColor(currentColor.getColor());
            g2d.drawString(subWord.toString(), locationX, locationY); //draw the last word, even if it's empty
            locationY += 23;

            //Reset to normal text color when we're done printing the title
            if (titleFlag) {
                titleFlag = false;
                currentColor = MCColor.GRAY;
            }
        }

        //If the user doesn't want a rarity, we don't print the extra lines at the end.
        if (itemRarity != Rarity.NONE) {
            locationY += 25;
            locationX = 10;
            g2d.setFont(minecraftBold);
            if (type.equalsIgnoreCase("NONE")) { type = " "; }

            g2d.setColor(itemRarity.getRarityColor().getBackgroundColor());
            g2d.drawString(itemRarity.getId() + " " + type.toUpperCase(), locationX + 2, locationY + 2);

            g2d.setColor(itemRarity.getRarityColor().getColor());
            g2d.drawString(itemRarity.getId() + " " + type.toUpperCase(), locationX, locationY);
        }

        g2d.dispose();

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", imageFile);
        event.getHook().sendFiles(FileUpload.fromData(imageFile)).setEphemeral(false).queue();

    }

    @Nullable
    private ArrayList<String> parseDescription(String description, GuildSlashEvent event) {
        ArrayList<String> parsed = new ArrayList<>();
        MCColor[] colors = MCColor.values();

        StringBuilder currString = new StringBuilder();
        int lineLength = 0; //where we are in curr string
        int charIndex = 0;  //where we are in description
        int breakLoopCount = 0; //break if we are hanging due to a runtime error

        //Go through the entire description, break it apart to put into an arraylist for rendering
        while(description.length() > charIndex) {
            //Make sure we're not looping infinitely and just hanging the thread
            breakLoopCount++;
            if (breakLoopCount > description.length() * 2) {
                String debug = "length: " + description.length() + "\n" +
                        "charIndex: " + charIndex + "\n" +
                        "character failed on: " + description.charAt(charIndex) + "\n" +
                        "string: " + description + "\n" +
                        "If you see this debug, please ping a developer. Thanks!\n";
                event.getHook().sendMessage(debug).setEphemeral(true).queue();
                return null;
            }

            boolean noColorFlag = false;
            /* This block checks colors, newline characters, soft-wrapping,
             * and changes the text depending on those checks.
             */
            if (description.length() != charIndex + 1) {
                //Color parsing
                if (description.charAt(charIndex) == '%' && description.charAt(charIndex + 1) == '%') {
                    int endCharIndex = 0;

                    //If a parameter can be passed, put that here.
                    StringBuilder specialSubString = new StringBuilder();
                    boolean specialSubStringFlag = false;
                    int specialSubStringIndex = -1;
                    for (int i = charIndex + 2; i < charIndex + 100; i++) { //get char
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

                        //Special case for a specialSubString
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

                    if (endCharIndex != -1) { //If we can't find the end percents, just continue
                        charIndex += 2; //move away from color code
                        String getSpecialString = description.substring(charIndex, endCharIndex);

                        boolean foundColor = false; //True if we find a valid color, stat, or gemstone.
                        for (MCColor color : colors) {
                            if (getSpecialString.equalsIgnoreCase(color.name())) {
                                foundColor = true;
                                currString.append("%%").append(color).append("%%");
                                break;
                            }
                        }

                        //redundant check so we don't call for stats without needing them
                        if (!foundColor) {
                            for (Stat stat : Stat.values()) {
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

                        //redundant check so we don't call for gems without needing them
                        if (!foundColor) {
                            for (Gemstone gemstone : Gemstone.values()) {
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
                            getSpecialString = getSpecialString.replaceAll("[^a-zA-Z0-9_ ]", "");
                            StringBuilder failed = new StringBuilder("You used an invalid code `" + getSpecialString + "`. Valid colors:\n");
                            for (MCColor color : colors) {
                                failed.append(color).append(" ");
                            }
                            failed.append("BOLD");
                            failed.append("\nValid Stats:\n");
                            for (Stat stat : Stat.values()) {
                                failed.append(stat).append(" ");
                            }
                            failed.append("\nValid Gems:\n");
                            for (Gemstone gemstone : Gemstone.values()) {
                                failed.append(gemstone).append(" ");
                            }
                            event.getHook().sendMessage(failed.toString()).setEphemeral(true).queue();
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
                    //if we can't find the endCharIndex, we just move on here and set a flag
                    noColorFlag = true;
                }

                //Shorthand Color Parsing
                if (description.charAt(charIndex) == '&' && description.charAt(charIndex + 1) != ' ') {
                    for(MCColor color : colors) {
                        if (color.getColorCode() == description.charAt(charIndex + 1)) {
                            currString.append("%%").append(color).append("%%");
                            break;
                        }
                    }
                    if ('l' == description.charAt(charIndex + 1)) {
                        currString.append("%%BOLD%%");
                    }
                    charIndex += 2;
                }

                //Newline parsing
                if (description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n') {
                    parsed.add(currString.toString());
                    currString.setLength(0);
                    lineLength = 0;
                    charIndex += 2;
                    continue;
                }

                //Softwrap parsing
                if (description.charAt(charIndex) == ' ') {
                    charIndex++;

                    int colorCheck = 36; //An extra buffer so we don't wrap colors
                    boolean newLineFlag = true; //True if we need to make a newline to paste the next word
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

                            //Let's see if there's a color here. We'll check if it's valid later.
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

                //EOL Parsing
                if (lineLength > 35) {
                    parsed.add(currString.toString());
                    currString.setLength(0);
                    lineLength = 0;
                    continue;
                }
            }

            //Find next break
            int findNextIndex = 0;
            boolean spaceBreak = false;
            for (int i = charIndex; i < description.length(); i++) {
                if (i + 1 >= description.length()) {
                    //Edge case for EOS
                    findNextIndex++;
                    break;
                }

                if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                    if (i + 2 >= description.length() || noColorFlag) {
                        //Edge case for EOS or if color has already been determined to not be present
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

                findNextIndex++;
            }

            //We're not at EOL yet, so let's write what we've got so far
            String subWriteString = description.substring(charIndex, charIndex + findNextIndex);
            currString.append(subWriteString).append(spaceBreak ? " " : ""); //if we need a space, put it in

            lineLength += findNextIndex;
            charIndex += findNextIndex;
        }

        //Make sure to save the last word written
        parsed.add(currString.toString());

        return parsed;
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.values()).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }
}
