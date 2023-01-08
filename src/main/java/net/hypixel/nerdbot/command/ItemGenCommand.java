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
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.MinecraftImage;
import net.hypixel.nerdbot.generator.StringColorParser;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
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

        // verify rarity argument
        String finalRarity = rarity;
        if (Arrays.stream(Rarity.VALUES).noneMatch(rarity1 -> finalRarity.equalsIgnoreCase(rarity1.name()))) {
            rarity = rarity.replaceAll("[^a-zA-Z0-9_ ]", "");
            StringBuilder failedRarity = new StringBuilder("You used an invalid rarity, `" + rarity + "`. Valid rarities:\n");
            Arrays.stream(Rarity.VALUES).forEachOrdered(rarity1 -> failedRarity.append(rarity1.name()).append(" "));
            event.getHook().sendMessage(failedRarity.toString()).setEphemeral(true).queue();
            return;
        }

        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        ArrayList<String> parsedDescription = StringColorParser.parseDescription(description, event);
        if (parsedDescription == null || parsedDescription.isEmpty()) {
            event.getHook().sendMessage("Please enter a valid description for the item!").setEphemeral(true).queue();
            return;
        }

        // adds the items name to the array list
        String createTitle = "%%" + itemRarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%";
        parsedDescription.add(0, createTitle);

        // writing the rarity if the rarity is not none
        if (itemRarity != Rarity.NONE) {
            if (type.equalsIgnoreCase("none")) {
                type = "";
            }
            // adds the items type in the description
            parsedDescription.add(parsedDescription.size(), "");
            String createRarity = "%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            
            parsedDescription.add(parsedDescription.size(), createRarity);
        }

        MinecraftImage minecraftImage = new MinecraftImage(500, parsedDescription.size(), MCColor.GRAY);
        minecraftImage.drawStrings(parsedDescription);

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(minecraftImage.getImage(), "png", imageFile);
        event.getHook().sendFiles(FileUpload.fromData(imageFile)).setEphemeral(false).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }
}
