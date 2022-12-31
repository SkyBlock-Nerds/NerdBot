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
import net.hypixel.nerdbot.util.MCColor;
import net.hypixel.nerdbot.util.Rarity;
import net.hypixel.nerdbot.util.Stats;
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

        event.deferReply(false).queue();

        //make sure user is in correct channel
        if (!senderChannelId.equals(itemGenChannelId)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannelId);
            if (channel == null) {
                event.getHook().sendMessage("Hi! This can only be used in the #item-gen channel.").queue();
                return;
            }

            event.getHook().sendMessage("Hi! This can only be used in the " + channel.getAsMention() + " channel.").queue();
            return;
        }

        //verify rarity argument
        if (Arrays.stream(Rarity.values()).noneMatch(rarity1 -> rarity.equalsIgnoreCase(rarity1.name()))) {
            StringBuilder failedRarity = new StringBuilder("Hi! We found an invalid rarity, " + rarity + ", which cannot be used here. Valid rarities:\n");
            Arrays.stream(Rarity.values()).forEachOrdered(rarity1 -> failedRarity.append(rarity1.name()).append("\n"));
            failedRarity.append(Arrays.toString(Rarity.values()));
            event.getHook().sendMessage(failedRarity.toString()).queue();
            return;
        }

        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        ArrayList<String> parsedDescription = parseDescription(description, event);
        if (parsedDescription == null || parsedDescription.isEmpty()) {
            event.getHook().sendMessage("Please enter a valid description for the item!").queue();
            return;
        }

        //Let's draw our image, parse our description
        int heightEstimate = ((4 + parsedDescription.size()) * 23) - 5;
        BufferedImage image = new BufferedImage(500, heightEstimate, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

          //Debug for printing out exactly what comes from the parser
//        StringBuilder temp = new StringBuilder();
//        for(String string : parsedDescription) {
//            temp.append(string).append("\n");
//        }
//        event.getHook().sendMessage(temp.toString()).queue();

        //Let's init our fonts
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
            event.getHook().sendMessage("Hi! Something went wrong with creating the font. Try again later!").queue();
            return;
        }

        //Let's generate and place our text
        int locationY = 25;
        int locationX = 10;

        g2d.setColor(itemRarity.getRarityColor());
        g2d.drawString(name, locationX, locationY);
        locationY += 23;
        g2d.setColor(MCColor.GRAY.getColor());

        //Go through our ArrayList, print each string on a new line
        boolean boldFlag = false;
        for (String line : parsedDescription) {
            locationX = 10;

            //Let's iterate through each character in our line, looking for colors
            StringBuilder subword = new StringBuilder();
            for(int colorStartIndex = 0; colorStartIndex < line.length(); colorStartIndex++) {
                //Check for colors
                if (colorStartIndex + 2 < line.length() && line.charAt(colorStartIndex) == '%' && line.charAt(colorStartIndex + 1) == '%') {
                    int colorEndIndex = -1;

                    for(int j = colorStartIndex; j < line.length() - 2; j++) {
                        if (line.charAt(j + 1) == '%' && line.charAt(j + 2) == '%') {
                            colorEndIndex = j;
                            break;
                        }
                    }

                    if (colorEndIndex != -1) {
                        //We've previously verified that this is a good color, so let's trust it
                        g2d.drawString(subword.toString(), locationX, locationY);
                        locationX += minecraftFont.getStringBounds(subword.toString(), g2d.getFontRenderContext()).getWidth();
                        subword.setLength(0);

                        String foundColor = line.substring(colorStartIndex + 2, colorEndIndex + 1);

                        if (foundColor.equalsIgnoreCase("bold")) {
                            g2d.drawString(subword.toString(), locationX, locationY);
                            locationX += minecraftFont.getStringBounds(subword.toString(), g2d.getFontRenderContext()).getWidth();
                            subword.setLength(0);
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftBold);
                            boldFlag = true;
                        }
                        else {
                            Arrays.stream(MCColor.values()).filter(color -> foundColor.equalsIgnoreCase(color.name())).findFirst().ifPresent(color -> g2d.setColor(color.getColor()));
                            colorStartIndex += 3 + foundColor.length(); //remove the color code
                            g2d.setFont(minecraftFont);
                            boldFlag = false;
                        }
                    }
                } else if (!minecraftFont.canDisplay(line.charAt(colorStartIndex))) {
                    //We need to draw this character special, so let's get rid of our old word.
                    g2d.drawString(subword.toString(), locationX, locationY);
                    if (boldFlag) {
                        locationX += minecraftBold.getStringBounds(subword.toString(), g2d.getFontRenderContext()).getWidth();
                    }
                    else {
                        locationX += minecraftFont.getStringBounds(subword.toString(), g2d.getFontRenderContext()).getWidth();
                    }
                    subword.setLength(0);

                    //Let's try to render the character in a normal font, and then return to the minecraft font.
                    Font tnr = new Font("SansSerif", Font.PLAIN, 20);
                    g2d.setFont(tnr);
                    subword.append(line.charAt(colorStartIndex));
                    g2d.drawString(subword.toString(), locationX, locationY);
                    locationX += tnr.getStringBounds(subword.toString(), g2d.getFontRenderContext()).getWidth();
                    subword.setLength(0);
                    g2d.setFont(boldFlag ? minecraftBold : minecraftFont);
                } else { //We do this to prevent monospace bullshit
                    subword.append(line.charAt(colorStartIndex));
                }
            }

            g2d.drawString(subword.toString(), locationX, locationY); //draw the last word, even if it's empty
            locationY += 23;
        }

        locationY += 25;
        locationX = 10;
        g2d.setFont(minecraftBold);
        g2d.setColor(itemRarity.getRarityColor());
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

        StringBuilder currString = new StringBuilder("");
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
                        "If you see this debug, please go ahead and ping Keith. Thanks!\n";
                event.getHook().sendMessage(debug).queue();
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

                    StringBuilder specialSubString = new StringBuilder(); //If a parameter can be passed, put that here.
                    boolean specialSubStringFlag = false;
                    int specialSubStringIndex = -1;
                    for (int i = charIndex + 2; i < charIndex + 100; i++) { //get char
                        if (i + 1 >= description.length()) {
                            endCharIndex = -1;
                            break;
                        }

                        //Very annoying corner case for when you want to have a percent as a stat
                        if (specialSubStringFlag && description.substring(i, i+3).equalsIgnoreCase("%%%")
                                && !description.substring(i, i+4).equalsIgnoreCase("%%%%")) {
                            endCharIndex = specialSubStringIndex;
                            specialSubString.append("%");
                            break;
                        }

                        if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                            if (specialSubStringFlag) {
                                endCharIndex = specialSubStringIndex;
                            }
                            else {
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

                        boolean foundColor = false;
                        for (MCColor color : colors) {
                            if (getSpecialString.equalsIgnoreCase(color.name())) {
                                //We've found a valid color but we're not going to action it here- we do that later
                                foundColor = true;
                                currString.append("%%").append(color).append("%%");
                                break;
                            }
                        }

                        for (Stats stat : Stats.values()) {
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

                        if (getSpecialString.equalsIgnoreCase("bold")) {
                            currString.append("%%BOLD%%");
                            foundColor = true;
                        }

                        if (!foundColor) {
                            StringBuilder failed = new StringBuilder("Hi! We found an invalid color or stat `" + getSpecialString + "` which cannot be used here. " +
                                    "Valid colors:\n");
                            for (MCColor color : colors) {
                                failed.append(color).append(" ");
                            }
                            failed.append("BOLD");
                            failed.append("\nValid stats:\n");
                            for (Stats stat : Stats.values()) {
                                failed.append(stat).append(" ");
                            }
                            event.getHook().sendMessage(failed.toString()).queue();
                            return null;
                        }

                        charIndex = endCharIndex + 2; //move away from color code
                        if (specialSubStringFlag) {
                            charIndex += specialSubString.length() + 1;
                            lineLength += specialSubString.length();
                        }
                        continue;
                    }
                    noColorFlag = true;
                    //if we can't find the endCharIndex, we just move on here and set a flag
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
                    boolean newLine = true;
                    charIndex++;

                    int colorCheck = 36;
                    for (int i = charIndex; i < charIndex + (colorCheck - lineLength); i++) {
                        if (i + 1 > description.length()) {
                            newLine = false;
                            break;
                        }
                        if (description.charAt(i) == ' ') {
                            newLine = false;
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

                    if (newLine) {
                        //If we get here, we need to be at a new line for the current word to be pasted
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
