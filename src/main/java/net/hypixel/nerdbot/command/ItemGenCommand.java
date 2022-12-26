package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.MCColor;
import net.hypixel.nerdbot.util.Rarity;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.Arrays;

@Log4j2
public class ItemGenCommand extends ApplicationCommand {

    @JDASlashCommand(name = "itemgen", description = "Creates a Skyblock item, visible to everyone in Skyblock Nerds.")
    public void askForInfo(
            GuildSlashEvent event,
            @AppOption(description = "The name of the item") String name,
            @AppOption(description = "The description of the item") String description,
            @AppOption(description = "The rarity of the item") String rarity
    ) throws IOException {
        MessageCreateBuilder builder = new MessageCreateBuilder();
        String senderChannelId = event.getChannel().getId();
        String itemGenChannelId = NerdBotApp.getBot().getConfig().getItemGenChannel();
        event.deferReply(true).queue();

        //make sure user is in correct channel
        if (!senderChannelId.equals(itemGenChannelId)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannelId);
            if (channel == null) {
                builder.addContent("Please use this in the correct channel!");
                return;
            }
            builder.addContent("Please use this in the ").addContent(channel.getAsMention()).addContent(" channel!");
            event.reply(builder.build()).setEphemeral(true).queue();
        }

        //verify rarity argument
        Rarity[] rarities = Rarity.values();
        Rarity foundRarity = null; //Used later to print out the rarity in a readable format
        for (Rarity rarity1 : rarities) {
            if (rarity1.toString().equalsIgnoreCase(rarity)) {
                foundRarity = rarity1;
                break;
            }
        }

        if (foundRarity == null) {
            builder.addContent("Please return a valid rarity: " + Arrays.toString(rarities));
            event.reply(builder.build()).setEphemeral(true).queue();
            return;
        }

        // Create an image, import fonts
        String estimateString = description.toLowerCase();
        MCColor[] colors = MCColor.values();
        for (MCColor color : colors) {
            String temp = "%%";
            temp += color.name().toLowerCase();
            temp += "%%";
            estimateString = estimateString.replace(temp, "");
        }

        //Try to estimate the image length
        int newlineInstances = 0;
        for(int i = 0; i < estimateString.length(); i++) {
            if (estimateString.charAt(i) == '\\' && estimateString.charAt(i + 1) == 'n') {
                newlineInstances++;
            }
        }
        estimateString = estimateString.replace("\\n", "");
        int heightEstimate = ((6 + newlineInstances + (estimateString.length() / 35)) * 20);

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
        }

        g2d.setFont(minecraftFont);

        //Let's generate and place our text
        int locationY = 22;
        int locationX = 10;

        g2d.setColor(foundRarity.getRarityColor());
        g2d.drawString(name, locationX, locationY);

        locationY += 40;
        g2d.setColor(MCColor.GRAY.getColor());

        //This goes through and parses colors, newlines, and soft-wraps the text.
        int lineLength = 0;
        int charIndex = 0;
        while(description.length() > charIndex) {
            //Color parsing
            if (description.charAt(charIndex) == '%' && description.charAt(charIndex + 1) == '%') {
                int endCharIndex = 0;

                for(int i = charIndex + 1; i < charIndex + 100; i++) { //get char
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

                    for (MCColor color : colors) {
                        if (getColor.equals(color.name().toLowerCase())) {
                            g2d.setColor(color.getColor());
                            break;
                        }
                    }

                    charIndex = endCharIndex + 2; //move away from color code
                }
                continue;
            }

            //Newline parsing
            if (description.charAt(charIndex) == '\\' && description.charAt(charIndex + 1) == 'n') {
                locationX = 10;
                locationY += 20;
                lineLength = 0;
                charIndex += 2;
                continue;
            }

            //Softwrap parsing
            if (description.charAt(charIndex) == ' ') {
                boolean newLine = true;
                charIndex++;

                for(int i = charIndex; i < charIndex + (34 - lineLength); i++) {
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
                    locationX = 10;
                    locationY += 20;
                    lineLength = 0;
                }
                continue;
            }

            //EOL Parsing
            if (lineLength > 35) {
                locationX = 10;
                locationY += 20;
                lineLength = 0;
                continue;
            }

            //Find next break
            int findNextIndex = 0;
            boolean spaceBreak = false;
            for (int i = charIndex; i < charIndex + (37 - lineLength); i++) {
                if (i + 1 > description.length()) {
                    break;
                }

                if (description.charAt(i) == '%' && description.charAt(i + 1) == '%') {
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
            String writeString = spaceBreak ? subWriteString + " " : subWriteString;
            g2d.drawString(writeString, locationX, locationY);
            lineLength += findNextIndex;
            charIndex += findNextIndex;
            locationX += minecraftFont.getStringBounds(writeString, g2d.getFontRenderContext()).getWidth();
        }

        locationY += 45;
        locationX = 10;
        g2d.setFont(minecraftBold);
        g2d.setColor(foundRarity.getRarityColor());
        g2d.drawString(foundRarity.getId(), locationX, locationY);

        g2d.dispose();

        File imageFile = File.createTempFile("image", ".png");
        event.getHook().sendFiles(FileUpload.fromData(imageFile)).queue();
    }
}
