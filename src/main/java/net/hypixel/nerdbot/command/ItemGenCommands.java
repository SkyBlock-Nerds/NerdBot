package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.AutocompletionMode;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.*;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.skyblock.MCColor;
import net.hypixel.nerdbot.util.skyblock.Rarity;
import net.hypixel.nerdbot.util.skyblock.Stat;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.hypixel.nerdbot.generator.GeneratorStrings.*;

@Log4j2
public class ItemGenCommands extends ApplicationCommand {
    private final GeneratorBuilder builder;

    public ItemGenCommands() {
        super();
        this.builder = new GeneratorBuilder();
    }

    @JDASlashCommand(name = "itemgen", subcommand = "item", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void generateItem(GuildSlashEvent event,
                             @AppOption(description = DESC_NAME) String name,
                             @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                             @AppOption(description = DESC_ITEM_LORE) String itemLore,
                             @Optional @AppOption(description = DESC_TYPE) String type,
                             @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                             @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                             @Optional @AppOption(description = DESC_PADDING) Integer padding,
                             @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();
        // building the item's description
        BufferedImage generatedImage = builder.buildItem(event, name, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "itemgen", subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event, @AppOption(description = DESC_TEXT) String message, @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();
        // building the chat message
        BufferedImage generatedImage = builder.buildItem(event, "NONE", "NONE", message, "", true, 0, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "itemgen", subcommand = "head", description = "Draws a minecraft head into a file")
    public void generateHead(GuildSlashEvent event,
                             @AppOption(description = DESC_HEAD_ID) String skinId,
                             @Optional @AppOption(description = DESC_IS_PLAYER_NAME) Boolean isPlayerName,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        BufferedImage head = builder.buildHead(event, skinId, isPlayerName);
        if (head != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(head))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "itemgen", subcommand = "full", description = "Generates a full item stack!")
    public void generateFullItem(GuildSlashEvent event,
                                 @AppOption(description = DESC_NAME) String name,
                                 @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                                 @AppOption(description = DESC_ITEM_LORE) String itemLore,
                                 @AppOption(description = DESC_HEAD_ID) String skinId,
                                 @Optional @AppOption(description = DESC_TYPE) String type,
                                 @Optional @AppOption(description = DESC_DISABLE_RARITY_LINEBREAK) Boolean disableRarityLinebreak,
                                 @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                                 @Optional @AppOption(description = DESC_PADDING) Integer padding,
                                 @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                                 @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden,
                                 @Optional @AppOption(description = DESC_IS_PLAYER_NAME) Boolean isPlayerName) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        MinecraftHead generatedHead = buildHead(event, skinId, isPlayerName);
        if (generatedHead == null) {
            return;
        }

        MinecraftImage generatedDescription = buildItem(event, name, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedDescription == null) {
            return;
        }

        ImageMerger merger = new ImageMerger(generatedDescription.getImage(), generatedHead.getImage());
        merger.drawFinalImage();
        event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();
    }

    @JDASlashCommand(name = "itemgen", subcommand = "parse", description = "Converts a minecraft item into a Nerd Bot item!")
    public void parseItemDescription(GuildSlashEvent event,
                                     @AppOption(description = DESC_PARSE_ITEM) String nbtDisplayTag,
                                     @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        JsonObject itemJSON;
        String itemName;
        JsonArray itemLoreArray;
        StringBuilder itemGenCommand = new StringBuilder("```/itemgen item");

        try {
            itemJSON = NerdBotApp.GSON.fromJson(nbtDisplayTag, JsonObject.class);
        } catch (JsonSyntaxException e) {
            event.getHook().sendMessage(GeneratorStrings.ITEM_PARSE_JSON_FORMAT).queue();
            return;
        }

        // checking that there is a name string in the JsonObject
        try {
            itemName = itemJSON.get("Name").getAsString().replaceAll("§", "&");
        } catch (NullPointerException e) {
            event.getHook().sendMessage(GeneratorStrings.MISSING_NAME_VARIABLE).queue();
            return;
        }

        // checking that there is a lore array in the JsonObject
        try {
            itemLoreArray = itemJSON.get("Lore").getAsJsonArray();
        } catch (NullPointerException e) {
            event.getHook().sendMessage(GeneratorStrings.MISSING_LORE_VARIABLE).queue();
            return;
        }

        // adding all the text to the string builders
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" name:").append(itemName).append(" rarity:NONE item_lore:");

        int maxLineLength = 0;
        for (JsonElement element : itemLoreArray) {
            String itemLore = element.getAsString().replaceAll("§", "&").replaceAll("`", "");
            itemText.append(itemLore).append("\\n");
            itemGenCommand.append(itemLore).append("\\n");

            if (maxLineLength < itemLore.length()) {
                maxLineLength = itemLore.length();
            }
        }
        itemGenCommand.replace(itemGenCommand.length() - 2, itemGenCommand.length(), "");
        itemGenCommand.append(" max_line_length:").append(maxLineLength).append("```");

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(StringColorParser.MAX_FINAL_LINE_LENGTH);
        colorParser.parseString(itemText);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            event.getHook().sendMessage(String.format(GeneratorStrings.ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return;
        }

        // creating the minecraft image and sending it to the user.
        MinecraftImage minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, StringColorParser.MAX_FINAL_LINE_LENGTH * 25, 255, 0, true).render();
        if (minecraftImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(minecraftImage.getImage()))).setEphemeral(false).queue();
        }

        event.getHook().sendMessage(String.format(GeneratorStrings.ITEM_PARSE_COMMAND, itemGenCommand)).setEphemeral(true).queue();
    }


    @JDASlashCommand(name = "itemgen", subcommand = "help", description = "Get a little bit of help with how to use the Generator bot.")
    public void askForInfo(GuildSlashEvent event) {
        if (isIncorrectChannel(event)) {
            return;
        }

        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder colorBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();
        infoBuilder.setColor(Color.CYAN)
                .setAuthor("SkyBlock Nerd Bot")
                .setTitle("Item Generation")
                .addField("Basic Info", ITEM_BASIC_INFO, true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments", ITEM_INFO_ARGUMENTS, false);

        colorBuilder.setColor(Color.YELLOW)
                .addField("Color Codes", ITEM_COLOR_CODES, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information", ITEM_OTHER_INFO, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(colorBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "itemgen", subcommand = "head_help", description = "Get a little bit of help with how to use the Head Rendering functions of the Generator bot.")
    public void askForRenderHelp(GuildSlashEvent event) {
        if (isIncorrectChannel(event)) {
            return;
        }

        EmbedBuilder infoBuilder = new EmbedBuilder();
        EmbedBuilder argumentBuilder = new EmbedBuilder();
        EmbedBuilder extraInfoBuilder = new EmbedBuilder();

        infoBuilder.setColor(Color.CYAN)
                .setAuthor("SkyBlock Nerd Bot")
                .setTitle("Head Generation")
                .addField("Basic Info", HEAD_INFO_BASIC, true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments", HEAD_INFO_ARGUMENTS, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information", HEAD_INFO_OTHER_INFORMATION, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "itemgen", subcommand = "statsymbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        event.reply(STAT_SYMBOLS).setEphemeral(true).queue();
    }

    @AutocompletionHandler(name = "rarities", mode = AutocompletionMode.CONTINUITY, showUserInput = false)
    public Queue<String> listRarities(CommandAutoCompleteInteractionEvent event) {
        return Stream.of(Rarity.VALUES).map(Enum::name).collect(Collectors.toCollection(ArrayDeque::new));
    }

    private boolean isIncorrectChannel(GuildSlashEvent event) {
        String senderChannelId = event.getChannel().getId();
        String[] itemGenChannelIds = NerdBotApp.getBot().getConfig().getItemGenChannel();

        if (itemGenChannelIds == null) {
            event.reply("The config for the item generating channel is not ready yet. Try again later!").setEphemeral(true).queue();
            return true;
        }

        if (Arrays.stream(itemGenChannelIds).noneMatch(senderChannelId::equalsIgnoreCase)) {
            // The top channel in the config should be considered the 'primary channel', which is referenced in the
            // error message.
            TextChannel channel = ChannelManager.getChannel(itemGenChannelIds[0]);
            if (channel == null) {
                event.reply("This can only be used in the item generating channel.").setEphemeral(true).queue();
                return true;
            }
            event.reply("This can only be used in the " + channel.getAsMention() + " channel.").setEphemeral(true).queue();
            return true;
        }

        return false;
    }

}

