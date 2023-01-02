package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import java.util.ArrayList;
import java.util.Arrays;

@Log4j2
public class ItemGenCommand extends ApplicationCommand {

    @JDASlashCommand(name = "itemgen", description = "Creates a SkyBlock item, visible to everyone in SkyBlock Nerds.")
    public void askForInfo(
            GuildSlashEvent event,
            @AppOption(description = "The name of the item") String name,
            @AppOption(description = "The description of the item") String description,
            @AppOption(description = "The rarity of the item") String rarity
    ) throws IOException {
        String senderChannelId = event.getChannel().getId();
        String itemGenChannelId = NerdBotApp.getBot().getConfig().getItemGenChannel();

        if (itemGenChannelId == null) {
            event.getHook().sendMessage("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue();

        if (!senderChannelId.equals(itemGenChannelId)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannelId);
            if (channel == null) {
                event.getHook().sendMessage("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return;
            }

            event.getHook().sendMessage("Hi! This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return;
        }

        //verify rarity argument
        if (Arrays.stream(Rarity.values()).noneMatch(rarity1 -> rarity.equalsIgnoreCase(rarity1.name()))) {
            StringBuilder failedRarity = new StringBuilder("You specified an invalid rarity, `" + rarity + "`. Valid rarities:\n");
            Arrays.stream(Rarity.values()).forEachOrdered(rarity1 -> failedRarity.append(rarity1.name()).append("\n"));
            failedRarity.append(Arrays.toString(Rarity.values()));
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
        //Let's draw our image, parse our description
        int heightEstimate = ((3 + parsedDescription.size()) * 23) - 5;
        BufferedImage image = new BufferedImage(500, heightEstimate, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(41, 5, 96));
        g2d.drawRect(1, 1, 497, heightEstimate - 3);
        g2d.drawRect(2, 2, 497, heightEstimate - 3);

        Font minecraftFont;
        Font minecraftBold;
        try {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, new File("./resources/Minecraft/minecraft.ttf")).deriveFont(16f);
            minecraftBold = Font.createFont(Font.TRUETYPE_FONT, new File("./resources/Minecraft/3_Minecraft-Bold.otf")).deriveFont(22f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(minecraftFont);
            ge.registerFont(minecraftBold);
            g2d.setFont(minecraftFont);
        } catch (IOException | FontFormatException e) {
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

                    for(int j = colorStartIndex; j < line.length() - 2; j++) {
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

        locationY += 25;
        locationX = 10;
        g2d.setFont(minecraftBold);

        g2d.setColor(itemRarity.getRarityColor().getBackgroundColor());
        g2d.drawString(itemRarity.getId(), locationX + 2, locationY + 2);

        g2d.setColor(itemRarity.getRarityColor().getColor());
        g2d.drawString(itemRarity.getId(), locationX, locationY);

        g2d.dispose();

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", imageFile);
        event.getHook().sendFiles(FileUpload.fromData(imageFile)).queue();
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
                        "If you see this debug, please report this to a developer. Thanks!\n";
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

                    StringBuilder specialSubString = new StringBuilder(); //If a color code has a special argument that can be used, we will store that in here
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

                    //If we can't find the end percents, just continue
                    if (endCharIndex != -1) {
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
                            StringBuilder failed = new StringBuilder("You specified an invalid code `" + getSpecialString + "`. Valid colors:\n");
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

                        charIndex = endCharIndex + 2; //move away from color code
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
}
