package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
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
            @Optional @AppOption(description = "The type of the item") String type
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

        StringBuilder itemLore = new StringBuilder(description);

        // adds the item's name to the array list
        Rarity itemRarity = Rarity.valueOf(rarity.toUpperCase());
        String createTitle = "%%" + itemRarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%\\n";
        itemLore.insert(0, createTitle);

        // writing the rarity if the rarity is not none
        if (itemRarity != Rarity.NONE) {
            if (type == null || type.equalsIgnoreCase("none")) {
                type = "";
            }
            // adds the items type in the description
            String createRarity = "\\n\\n%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        }

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser();
        colorParser.parseString(itemLore);

        // checking that there were no errors while parsing the string
        if (!colorParser.didSuccessfullyParsed()) {
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return;
        }

        // checking that the font's have been loaded into memory correctly
        if (!MinecraftImage.registerFonts()) {
            event.getHook().sendMessage("It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!").setEphemeral(true).queue();
            return;
        }

        MinecraftImage minecraftImage = new MinecraftImage(500, colorParser.requiredLines(), MCColor.GRAY);
        minecraftImage.drawStrings(colorParser.getParsedDescription());

        File imageFile = File.createTempFile("image", ".png");
        ImageIO.write(minecraftImage.getImage(), "png", imageFile);
        event.getHook().sendFiles(FileUpload.fromData(imageFile)).setEphemeral(false).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }
}
