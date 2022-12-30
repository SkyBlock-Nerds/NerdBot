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
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

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
        assert parsedDescription != null;
        int heightEstimate = ((4 + parsedDescription.size()) * 20);

        //Let's draw our image
        BufferedImage image = new BufferedImage(500, heightEstimate, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        //Let's init our fonts
        Font minecraftFont = null;
        Font minecraftBold = null;
        try {
            minecraftFont = Font.createFont(Font.TRUETYPE_FONT, new File("./resources/Minecraft/minecraft.ttf")).deriveFont(16f);
            minecraftBold = Font.createFont(Font.TRUETYPE_FONT, new File("./resources/Minecraft/3_Minecraft-Bold.otf")).deriveFont(22f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(minecraftFont);
            ge.registerFont(minecraftBold);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
            event.getHook().sendMessage("Hi! Something went wrong with creating the font. Try again later!").queue();
            return;
        }

        g2d.setFont(minecraftFont);

        //Let's generate and place our text
        int locationY = 25;
        int locationX = 10;

        g2d.setColor(itemRarity.getRarityColor());
        g2d.drawString(name, locationX, locationY);

        locationY += 20;
        g2d.setColor(MCColor.GRAY.getColor());

        for(String line : parsedDescription) {
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
                        for (MCColor color : MCColor.values()) {
                            if (foundColor.equalsIgnoreCase(color.name())) {
                                g2d.setColor(color.getColor());
                                break;
                            }
                        }

                        colorStartIndex += 3 + foundColor.length(); //remove the color code
                    }
                }
                else { //We do this to prevent monospace bullshit
                    subword.append(line.charAt(colorStartIndex));
                }
            }

            g2d.drawString(subword.toString(), locationX, locationY); //draw the last word, even if it's empty
            locationY += 20;
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

        //todo: remove
        StringBuilder tempsend = new StringBuilder("");
        for (String string : Objects.requireNonNull(parseDescription(description, event))) {
            tempsend.append(string).append("\n");
        }
        event.getHook().sendMessage(tempsend.toString()).queue();
    }

    @Nullable
    private ArrayList<String> parseDescription(String description, GuildSlashEvent event) {
        ArrayList<String> parsed = new ArrayList<String>(); //let's just say there's a hypothetical 30 line cap on strings
        MCColor[] colors = MCColor.values();

        StringBuilder currString = new StringBuilder("");
        int lineLength = 0; //where we are in curr string
        int charIndex = 0;  //where we are in description
        int breakLoopCount = 0;
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

                    for (int i = charIndex + 2; i < charIndex + 100; i++) { //get char
                        if (i + 1 >= description.length()) {
                            endCharIndex = -1;
                            break;
                        }

                        if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
                            endCharIndex = i;
                            break;
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
                        String getColor = description.substring(charIndex, endCharIndex);

                        boolean foundColor = false;
                        for (MCColor color : colors) {
                            if (getColor.equalsIgnoreCase(color.name())) {
                                //We've found a valid color but we're not going to action it here- we do that later
                                foundColor = true;
                                //lineLength -= 4 + getColor.length();
                                currString.append("%%").append(color).append("%%");
                                break;
                            }
                        }

                        if (!foundColor) {
                            StringBuilder failed = new StringBuilder("Hi! We found an invalid color or stat `" + getColor + "` which cannot be used here. " +
                                    "Valid colors:\n");
                            for (MCColor color : colors) {
                                failed.append(color).append(" ");
                            }
                            failed.append("\nValid rarities:\nTODO");

                            event.getHook().sendMessage(failed.toString()).queue();
                            return null;
                        }

                        charIndex = endCharIndex + 2; //move away from color code
                        continue;
                    }
                    noColorFlag = true;
                    //if we can't find the endCharIndex, we just move on here and set a flag
                }

                //Newline parsing
                if (description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n') {
                    System.out.println("Made a new line due to a newline character!");
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

                    for (int i = charIndex; i < charIndex + (38 - lineLength); i++) {
                        if (i + 1 > description.length()) {
                            newLine = false;
                            break;
                        }
                        if (description.charAt(i) == ' ') {
                            newLine = false;
                            break;
                        }
                    }

                    if (newLine) {
                        //If we get here, we need to be at a new line for the current word to be pasted
                        System.out.println("Made a new line due to softwrapping!");
                        parsed.add(currString.toString());
                        currString.setLength(0);
                        lineLength = 0;
                    }
                    continue;
                }

                //EOL Parsing
                if (lineLength > 35) {
                    System.out.println("Made a new line due to the EOL check!");
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

                if (description.charAt(i) == ' ') {
                    spaceBreak = true;
                    break;
                }

                findNextIndex++;
            }

            String subWriteString = description.substring(charIndex, charIndex + findNextIndex);

            currString.append(subWriteString).append(spaceBreak ? " " : "");

            lineLength += findNextIndex;
            charIndex += findNextIndex;
        }
        parsed.add(currString.toString());

        return parsed;
    }
}
