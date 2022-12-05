package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelManager;
import net.hypixel.nerdbot.util.Rarity;

import java.io.IOException;
import java.util.EnumSet;

@Log4j2
public class ItemGenCommand extends ApplicationCommand {
    @JDASlashCommand(name = "itemgen", description = "Creates a Skyblock item")

    public void askForInfo(GuildSlashEvent event,
                           @AppOption(description = "The name of the item") String name,
                           @AppOption(description = "The description of the item") String description,
                           @AppOption(description = "The rarity of the item") String rarity) throws IOException {
        StringBuilder builder = new StringBuilder();

        String senderChannel = event.getChannel().getId();
        String itemGenChannel = NerdBotApp.getBot().getConfig().getItemGenChannel();

        //make sure user is in correct channel
        if (!senderChannel.equals(itemGenChannel)) {
            TextChannel channel = ChannelManager.getChannel(itemGenChannel);
            if (channel == null) {
                builder.append("Please use this in the #item-gen channel!");
                return;
            }
            builder.append("Please use this in the ").append(channel.getAsMention()).append(" channel!");
            event.reply(builder.toString()).setEphemeral(true).queue();
        }

        boolean flagRarityFound = false;
        Rarity[] rarities = Rarity.values();
        for (Rarity rarity1 : rarities) {
            if (rarity1.toString().equalsIgnoreCase(rarity)) {
                flagRarityFound = true;
                break;
            }
        }

        if (!flagRarityFound) {
            builder.append("Please return a valid rarity:");
            for (Rarity rarity1 : rarities) {
                builder.append("\n").append(rarity1.toString());
            }
            event.reply(builder.toString()).setEphemeral(true).queue();
            return;
        }

        builder.append(name).append("\n----------\n").append(description).append("\n----------\n").append(rarity);

        //Sends the message built in builder to the channel
        event.reply(builder.toString()).setEphemeral(false).queue();
    }
}
