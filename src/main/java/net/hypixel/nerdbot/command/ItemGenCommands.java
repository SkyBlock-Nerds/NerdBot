package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.MinecraftImage;
import net.hypixel.nerdbot.generator.StringColorParser;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class ItemGenCommands extends ApplicationCommand {

    @JDASlashCommand(name = "itemgen", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void askForInfo(
            GuildSlashEvent event,
            @AppOption(description = "The name of the item") String name,
            @AppOption(description = "The rarity of the item", autocomplete = "rarities") String rarity,
            @AppOption(description = "The description of the item") String description,
            @Optional @AppOption(description = "The type of the item") String type,
            @Optional @AppOption(description = "If you will handle line breaks at the end of the item's description") Boolean handleLineBreaks,
            @Optional @AppOption(description = "Sets the background transparency level, 0 for transparent, 255 for opaque") Integer alpha,
            @Optional @AppOption(description = "Sets the transparent padding around the image, 0 for none, 1 for discord") Integer padding
    ) throws IOException {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getItemGenChannel();

        event.deferReply(false).queue();

        if (itemGenChannelIds == null) {
            event.getHook().sendMessage("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return;
        }

        if (Arrays.stream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            //The top channel in the config should be considered the 'primary channel', which is referenced in the
            //error message.
            TextChannel channel = ChannelManager.getChannel(itemGenChannelIds[0]);
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
        if (!name.equalsIgnoreCase("NONE")) { // allow user to pass NONE for the title
            String createTitle = "%%" + itemRarity.getRarityColor().toString() + "%%" + name + "%%GRAY%%\\n";
            itemLore.insert(0, createTitle);
        }

        // writing the rarity if the rarity is not none
        if (itemRarity != Rarity.NONE) {
            // checks if there is a type for the item
            if (type == null || type.equalsIgnoreCase("none")) {
                type = "";
            }
            // checking if there is custom line break happening
            if (handleLineBreaks == null || !handleLineBreaks) {
                itemLore.append("\\n");
            }

            // adds the items type in the description
            String createRarity = "\\n%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        }

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser();
        colorParser.parseString(itemLore);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return;
        }

        // checking that the font's have been loaded into memory correctly
        if (!MinecraftImage.isFontsRegistered()) {
            event.getHook().sendMessage("It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!").setEphemeral(true).queue();
            return;
        }

        // alpha value validation
        alpha = Objects.requireNonNullElse(alpha, 255); // checks if the image transparency was set
        alpha = Math.min(255, Math.max(alpha, 0)); // ensure range between 0-254

        // padding value validation
        padding = Objects.requireNonNullElse(padding, 0);
        padding = Math.max(0, padding);

        File minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, 500, alpha, padding)
            .render()
            .toFile();

        event.getHook().sendFiles(FileUpload.fromData(minecraftImage)).setEphemeral(false).queue();

        Member member = event.getMember();
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), member.getId());
        if (discordUser == null) {
            log.info("Not updating last item generator activity date for " + member.getEffectiveName() + " (ID: " + member.getId() + ") since we cannot find a user!");
            return;
        }

        discordUser.getLastActivity().setLastItemGenUsage(System.currentTimeMillis());
        log.info("Updating last item generator activity date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }

    @JDASlashCommand(name = "infogen", description = "Get a little bit of help with how to use the Generator bot.")
    public void askForInfo(GuildSlashEvent event) throws IOException {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getItemGenChannel();

        if (itemGenChannelIds == null) {
            event.reply("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return;
        }

        if (Arrays.stream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            //The top channel in the config should be considered the 'primary channel', which is referenced in the
            //error message.
            TextChannel channel = ChannelManager.getChannel(itemGenChannelIds[0]);
            if (channel == null) {
                event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return;
            }
            event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Welcome to the Item Generator bot!\n");
        builder.append("This is a bot used to create custom items to be used in suggestions. You can use the bot with /itemgen, and it accepts a few various arguments:\n\n");
        builder.append("`name:` The name of the item. Defaults to the rarity color, unless the rarity is none.\n");
        builder.append("`rarity:` Takes any SkyBlock rarity. Can be left as NONE.\n");
        builder.append("`description:` Parses a description, including color codes, bold, italics, and newlines.\n");
        builder.append("`type:` The type of the item, such as a Sword or Wand. Can be left blank.\n");
        builder.append("`handle_line_breaks- (true/false)`: To be used if you're manually handling line breaks between the description and rarity.\n");
        builder.append("`alpha:`: Sets the transparency of the background layer. 0 for transparent, 255 for opaque (default). 245 for overlay.\n");
        builder.append("`padding:`: Adds transparency around the entire image. Must be 0 (default) or higher.\n\n");
        builder.append("The Item Generator bot also accepts color codes. You can use these with either manual Minecraft codes, such as `&1`, or `%%DARK_BLUE%%`.\n");
        builder.append("You can use this same format for stats, such as `%%PRISTINE%%`. \nThis format can also have numbers, where `%%PRISTINE:1%%` will become \"1 ✧ Pristine\"\n");
        builder.append("Finally, you can move your text to a newline by using \\n. This format can be forced with the handle_line_breaks argument.\n\n");
        builder.append("Have fun making items! You can click the blue /itemgen command above anyone's image to see what command they're using to create their image. Thanks!\n\n");
        builder.append("The item generation bot is maintained by mrkeith. Feel free to tag him with any issues.");


        event.reply(builder.toString()).setEphemeral(true).queue();
    }
}
