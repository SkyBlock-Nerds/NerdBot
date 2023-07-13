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

@Log4j2
public class ItemGenCommands extends ApplicationCommand {


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

        MinecraftImage generatedImage = buildItem(event, name, rarity, itemLore, type, disableRarityLinebreak, alpha, padding, maxLineLength, true);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage.getImage()))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "itemgen", subcommand = "text", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event, @AppOption(description = DESC_TEXT) String message, @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        MinecraftImage generatedImage = buildItem(event, "NONE", "NONE", message, "", true, 0, 1, StringColorParser.MAX_FINAL_LINE_LENGTH, false);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage.getImage()))).setEphemeral(hidden).queue();
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

        MinecraftHead head = buildHead(event, skinId, isPlayerName);
        if (head != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(head.getImage()))).setEphemeral(hidden).queue();
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
                .addField("Basic Info", "This is a bot used to create custom items to be used in suggestions. You can use the bot with `/itemgen item`, `/itemgen head`, and `/itemgen full`.", true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments",
                        """
                        `name`: The name of the item. Defaults to the rarity color, unless the rarity is none.
                        `rarity`: Takes any SkyBlock rarity. Can be left as NONE.
                        `item_lore`: Parses a description, including color codes, bold, italics, and newlines.
                        `type`: The type of the item, such as a Sword or Wand. Can be left blank.
                        `disable_rarity_linebreak (true/false)`: To be used if you want to disable automatically adding the empty line between the item lore and rarity.
                        `alpha`: Sets the transparency of the background layer. 0 for transparent, 255 for opaque (default). 245 for overlay.
                        `padding`: Adds transparency around the entire image. Must be 0 (default) or higher.
                        `max_line_length`: Defines the maximum length that the line can be. Can be between 1 and 54.
                        """, false);

        colorBuilder.setColor(Color.YELLOW)
                .addField("Color Codes",
                        """
                        The Item Generator bot also accepts color codes. You can use these with either manual Minecraft codes, such as `&1`, or Hypixel style color codes, such as `%%DARK_BLUE%%`.
                        You can use this same format for stats, such as `%%PRISTINE%%`. This format can also have numbers, where `%%PRISTINE:+1%%` will become "+1 ✧ Pristine".
                        If you just want to get the icon for a specific stat, you can use `%%&PRISTINE%%` to automatically format it to the correct color, or retrieve it manually from the `/statsymbols` command.
                        Finally, you can move your text to a newline by typing `\\n`. If you don't want the extra line break at the end, set the `disable_rarity_linebreak` argument to True.
                        """, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information",
                        """
                        There is another command `/itemgen parse` which can be used to easily convert the display NBT Tag from a Minecraft item into a Generated Image. This display tag should be surrounded with curly brackets with a "Lore" (string array) and "Name" (string) attribute in them
                        You can also check out `/itemgen head_help` for more information about rendering items next to your creations!
                        Have fun making items! You can click the blue /itemgen command above anyone's image to see what command they're using to create their image. Thanks!
                        The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                        """, false);

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
                .addField("Basic Info", "The command `/itemgen head` which will display a rendered Minecraft Head from a Skin (or player) you chose!", true);

        argumentBuilder.setColor(Color.GREEN)
                .addField("Arguments",
                        """
                        `skin_id:` The skin ID or the player name of the person you wish to grab the skin from. (This is the string written after `http://textures.minecraft.net/texture/...`
                        `is_player_head:` set to True if the skin ID is a player's name
                        """, false);

        extraInfoBuilder.setColor(Color.GRAY)
                .addField("Other Information",
                        """
                        If you are feeling extra spicy, you can combine these two elements by using the `/itemgen full` command with arguments mentioned previously.
                        The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                        """, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "itemgen", subcommand = "statsymbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        event.reply(GeneratorStrings.STAT_SYMBOLS).setEphemeral(true).queue();
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

    private MinecraftImage buildItem(GuildSlashEvent event, String name, String rarity, String itemLoreString, String type,
                                     Boolean addEmptyLine, Integer alpha, Integer padding, Integer maxLineLength, boolean isChatMessage) {
        // Checking that the fonts have been loaded correctly
        if (!MinecraftImage.isFontsRegistered()) {
            event.getHook().sendMessage(GeneratorStrings.FONTS_NOT_REGISTERED).setEphemeral(true).queue();
            return null;
        }

        // verify rarity argument
        if (Arrays.stream(Rarity.VALUES).noneMatch(rarity1 -> rarity.equalsIgnoreCase(rarity1.name()))) {
            event.getHook().sendMessage(String.format(GeneratorStrings.INVALID_RARITY, GeneratorStrings.stripString(rarity))).setEphemeral(true).queue();
            return null;
        }

        StringBuilder itemLore = new StringBuilder(itemLoreString);

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
            if (addEmptyLine == null || !addEmptyLine) {
                itemLore.append("\\n");
            }

            // adds the items type in the item lore
            String createRarity = "\\n%%" + itemRarity.getRarityColor() + "%%%%BOLD%%" + itemRarity.getId().toUpperCase() + " " + type;
            itemLore.append(createRarity);
        } else {
            itemLore.append("\\n");
        }

        maxLineLength = Objects.requireNonNullElse(maxLineLength, StringColorParser.MAX_STANDARD_LINE_LENGTH);
        maxLineLength = Math.min(StringColorParser.MAX_FINAL_LINE_LENGTH, Math.max(1, maxLineLength));
        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(maxLineLength);
        colorParser.parseString(itemLore);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return null;
        }

        // alpha value validation
        alpha = Objects.requireNonNullElse(alpha, 255); // checks if the image transparency was set
        alpha = Math.min(255, Math.max(alpha, 0)); // ensure range between 0-254

        // padding value validation
        padding = Objects.requireNonNullElse(padding, 0);
        padding = Math.max(0, padding);

        MinecraftImage minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, maxLineLength * 25, alpha, padding, isChatMessage).render();

        Member member = event.getMember();
        DiscordUser discordUser = Util.getOrAddUserToCache(NerdBotApp.getBot().getDatabase(), member.getId());
        if (discordUser == null) {
            log.info("Not updating last item generator activity date for " + member.getEffectiveName() + " (ID: " + member.getId() + ") since we cannot find a user!");
        } else {
            discordUser.getLastActivity().setLastItemGenUsage(System.currentTimeMillis());
            log.info("Updating last item generator activity date for " + member.getEffectiveName() + " to " + System.currentTimeMillis());
        }

        return minecraftImage;
    }

    private MinecraftHead buildHead(GuildSlashEvent event, String textureID, Boolean isPlayerName) {
        if (isPlayerName != null && isPlayerName) {
            textureID = getPlayerHeadURL(event, textureID);
            if (textureID == null) {
                return null;
            }
        }

        if (textureID.contains("http://textures.minecraft.net/texture/")) {
            textureID = textureID.replace("http://textures.minecraft.net/texture/", "");
            event.getHook().sendMessage(GeneratorStrings.HEAD_URL_REMINDER).setEphemeral(true).queue();
        }

        BufferedImage skin;
        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureID);
            skin = ImageIO.read(target);
        } catch (MalformedURLException e) {
            event.getHook().sendMessage(GeneratorStrings.MALFORMED_HEAD_URL).setEphemeral(false).queue();
            return null;
        } catch (IOException e) {
            event.getHook().sendMessage(String.format(GeneratorStrings.INVALID_HEAD_URL, GeneratorStrings.stripString(textureID))).setEphemeral(false).queue();
            return null;
        }

        return new MinecraftHead(skin).drawHead();
    }

    private String getPlayerHeadURL(GuildSlashEvent event, String playerName) {
        playerName = GeneratorStrings.stripString(playerName);

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(GeneratorStrings.REQUEST_PLAYER_UUID_ERROR).queue();
            return null;
        }

        if (userUUID == null || userUUID.get("id") == null) {
            event.getHook().sendMessage(GeneratorStrings.PLAYER_NOT_FOUND).queue();
            return null;
        }

        JsonObject userProfile;
        try {
            userProfile = Util.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(GeneratorStrings.REQUEST_PLAYER_UUID_ERROR).queue();
            return null;
        }

        if (userProfile == null || userProfile.get("properties") == null) {
            event.getHook().sendMessage(String.format(GeneratorStrings.MALFORMED_PLAYER_PROFILE, GeneratorStrings.stripString(playerName))).queue();
            return null;
        }

        String base64SkinData = userProfile.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);

        String finalSkinID = skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();
        finalSkinID = finalSkinID.replace("http://textures.minecraft.net/texture/", "");

        return finalSkinID;
    }
}

