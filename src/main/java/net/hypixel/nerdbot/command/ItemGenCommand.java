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
import net.hypixel.nerdbot.util.Rarity;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        BufferedImage image = new BufferedImage(500, (6 + (description.length() / 35)) * 20, BufferedImage.TYPE_INT_RGB); //attempt to guess how long the image should be
        Graphics2D g2d = image.createGraphics();

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

        int locationY = 22;
        //Let's generate and place our text
        g2d.setColor(foundRarity.getColor());
        g2d.drawString(name, 10, locationY);

        locationY += 40;
        g2d.setColor(Color.GRAY);

        while(description.length() > 35) {
            g2d.drawString(description.substring(0, 35), 10, locationY);
            description = description.substring(35);
            locationY += 20;
        }

        g2d.drawString(description, 10, locationY);

        locationY += 45;
        g2d.setFont(minecraftBold);
        g2d.setColor(foundRarity.getColor());
        g2d.drawString(foundRarity.getId(), 10, locationY);

        g2d.dispose();

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", imageFile);
        builder.addFiles(FileUpload.fromData(imageFile));
//        builder.addContent(name)
//                .addContent("\n----------\n")
//                .addContent(description)
//                .addContent("\n----------\n")
//                .addContent(foundRarity.getId());
        event.reply(builder.build()).setEphemeral(false).queue();
    }
}
