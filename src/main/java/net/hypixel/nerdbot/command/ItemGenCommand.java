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
import java.util.List;

@Log4j2
public class ItemGenCommand extends ApplicationCommand {
    @JDASlashCommand(name = "itemgen", description = "Creates a Skyblock item, visible to everyone in Skyblock Nerds.")

    public void askForInfo(GuildSlashEvent event,
                           @AppOption(description = "The name of the item") String name,
                           @AppOption(description = "The description of the item") String description,
                           @AppOption(description = "The rarity of the item") String rarity) throws IOException {
        MessageCreateBuilder builder = new MessageCreateBuilder();

        String senderChannel = event.getChannel().getId();
        String itemGenChannel = NerdBotApp.getBot().getConfig().getItemGenChannel();

        //make sure user is in correct channel
        if (!senderChannel.equals(itemGenChannel)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannel);
            if (channel == null) {
                builder.addContent("Please use this in the correct channel!");
                return;
            }
            builder.addContent("Please use this in the ").addContent(channel.getAsMention()).addContent(" channel!");
            event.reply(builder.build()).setEphemeral(true).queue();
            event.reply(builder.build()).setEphemeral(true).queue();
        }

        boolean flagRarityFound = false;
        Rarity[] rarities = Rarity.values();
        Rarity foundRarity = null; //Used later to print out the rarity in a readable format

        for (Rarity rarity1 : rarities) {
            if (rarity1.toString().equalsIgnoreCase(rarity)) {
                flagRarityFound = true;
                foundRarity = rarity1;
                break;
            }
        }

        if (!flagRarityFound) {
            builder.addContent("Please return a valid rarity:");
            for (Rarity rarity1 : rarities) {
                builder.addContent("\n").addContent(rarity1.toString());
            }
            event.reply(builder.build()).setEphemeral(true).queue();
            return;
        }

        BufferedImage image = new BufferedImage(500, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        //this looks and feels horrible
        g2d.setColor(foundRarity.getColor());
        g2d.drawString(name, 10, 20);

        g2d.setColor(Color.GRAY);
        g2d.drawString(description, 10, 40);

        g2d.setColor(foundRarity.getColor());
        g2d.drawString(foundRarity.getId(), 10, 60);

        g2d.dispose();

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", imageFile);

        List<FileUpload> files = new ArrayList<>();
        files.add(FileUpload.fromData(imageFile));

        builder.addFiles(files);

        builder.addContent(name)
                .addContent("\n----------\n")
                .addContent(description)
                .addContent("\n----------\n")
                .addContent(foundRarity.getId());

        event.reply(builder.build()).setEphemeral(false).queue();
    }
}
