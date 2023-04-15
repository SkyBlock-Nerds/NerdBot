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
import net.hypixel.nerdbot.api.database.user.DiscordUser;
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
    private static final String DESC_NAME = "The name of the item";
    private static final String DESC_RARITY = "The rarity of the item";
    private static final String DESC_DESCRIPTION = "The description of the item";
    private static final String DESC_TEXT = "The text to display";
    private static final String DESC_TYPE = "The type of the item";
    private static final String DESC_HANDLE_LINE_BREAKS = "If you will handle line breaks at the end of the item's description";
    private static final String DESC_ALPHA = "Sets the background transparency level (0 = transparent, 255 = opaque)";
    private static final String DESC_PADDING = "Sets the transparent padding around the image (0 = none, 1 = discord)";
    private static final String DESC_MAX_LINE_LENGTH = "Sets the maximum length for a line (1 - " + StringColorParser.MAX_FINAL_LINE_LENGTH + ") default " + StringColorParser.MAX_STANDARD_LINE_LENGTH;
    private static final String DESC_HEAD_ID = "The ID of the skin or the Player Name (set is_player_name to True if it is a player name)";
    private static final String DESC_IS_PLAYER_NAME = "If the skin ID given describes the player's name";
    private static final String DESC_HIDDEN = "If you only want the generated image visible to yourself";
    private static final String DESC_PARSE_ITEM = "Item JSON Display Data (in the form {\"Lore\": [...], \"Name\": \"\"}";
    private static final String NAME_TEXT = "text";

    @JDASlashCommand(name = "textgen", description = "Creates an image that looks like a message from Minecraft, primarily used for Hypixel Skyblock")
    public void generateText(GuildSlashEvent event, @AppOption(description = DESC_TEXT, name = NAME_TEXT) String description, @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        MinecraftImage generatedImage = buildItem(event, "NONE", "NONE", description, "", true, 0, 1, StringColorParser.MAX_FINAL_LINE_LENGTH);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage.getImage()))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "itemgen", description = "Creates an image that looks like an item from Minecraft, primarily used for Hypixel SkyBlock")
    public void generateItem(GuildSlashEvent event,
                             @AppOption(description = DESC_NAME) String name,
                             @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                             @AppOption(description = DESC_DESCRIPTION) String description,
                             @Optional @AppOption(description = DESC_TYPE) String type,
                             @Optional @AppOption(description = DESC_HANDLE_LINE_BREAKS) Boolean handleLineBreaks,
                             @Optional @AppOption(description = DESC_ALPHA) Integer alpha,
                             @Optional @AppOption(description = DESC_PADDING) Integer padding,
                             @Optional @AppOption(description = DESC_MAX_LINE_LENGTH) Integer maxLineLength,
                             @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        MinecraftImage generatedImage = buildItem(event, name, rarity, description, type, handleLineBreaks, alpha, padding, maxLineLength);
        if (generatedImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(generatedImage.getImage()))).setEphemeral(hidden).queue();
        }
    }

    @JDASlashCommand(name = "headgen", description = "Draws a minecraft head into a file")
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

    @JDASlashCommand(name = "fullgen", description = "Generates a full item stack!")
    public void generateFullItem(GuildSlashEvent event,
                                 @AppOption(description = DESC_NAME) String name,
                                 @AppOption(description = DESC_RARITY, autocomplete = "rarities") String rarity,
                                 @AppOption(description = DESC_DESCRIPTION) String description,
                                 @AppOption(description = DESC_HEAD_ID) String skinId,
                                 @Optional @AppOption(description = DESC_TYPE) String type,
                                 @Optional @AppOption(description = DESC_HANDLE_LINE_BREAKS) Boolean handleLineBreaks,
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

        MinecraftImage generatedDescription = buildItem(event, name, rarity, description, type, handleLineBreaks, alpha, padding, maxLineLength);
        if (generatedDescription == null) {
            return;
        }

        ImageMerger merger = new ImageMerger(generatedDescription.getImage(), generatedHead.getImage());
        merger.drawFinalImage();
        event.getHook().sendFiles(FileUpload.fromData(Util.toFile(merger.getImage()))).setEphemeral(hidden).queue();
    }

    @JDASlashCommand(name = "itemgenparse", description = "Converts a minecraft item into a Nerd Bot item!")
    public void parseItemDescription(GuildSlashEvent event,
                                     @AppOption(description = DESC_PARSE_ITEM) String description,
                                     @Optional @AppOption(description = DESC_HIDDEN) Boolean hidden) throws IOException {
        if (isIncorrectChannel(event)) {
            return;
        }
        hidden = (hidden != null && hidden);
        event.deferReply(hidden).queue();

        JsonObject itemJSON;
        String itemName;
        JsonArray itemLoreArray;
        StringBuilder itemGenCommand = new StringBuilder("```/itemgen");

        try {
            itemJSON = NerdBotApp.GSON.fromJson(description, JsonObject.class);
        } catch (JsonSyntaxException e) {
            event.getHook().sendMessage("""
                                        This JSON object seems to not be valid. It should follow a similar format to...
                                        ```json
                                        {"Lore": ["lore array goes here"], "Name": "item name goes here"}
                                        ```""").queue();
            return;
        }

        // checking that there is a name string in the JsonObject
        try {
            itemName = itemJSON.get("Name").getAsString().replaceAll("§", "&");
        } catch (NullPointerException e) {
            event.getHook().sendMessage("It seems that you are missing a `Name` variable in your item's display tag").queue();
            return;
        }

        // checking that there is a lore array in the JsonObject
        try {
            itemLoreArray = itemJSON.get("Lore").getAsJsonArray();
        } catch (NullPointerException e) {
            event.getHook().sendMessage("It seems that you are missing a `Lore` variable in your item's display tag").queue();
            return;
        }

        // adding all the text to the string builders
        StringBuilder itemText = new StringBuilder();
        itemText.append(itemName).append("\\n");
        itemGenCommand.append(" name:").append(itemName).append(" rarity:NONE description:");

        for (JsonElement element : itemLoreArray) {
            String itemLore = element.getAsString().replaceAll("§", "&");
            itemText.append(itemLore).append("\\n");
            itemGenCommand.append(itemLore.replaceAll("`", "")).append("\\n");
        }
        itemGenCommand.replace(itemGenCommand.length() - 2, itemGenCommand.length(), "");
        itemGenCommand.append("```");

        // creating a string parser to convert the string into color flagged text
        StringColorParser colorParser = new StringColorParser(StringColorParser.MAX_FINAL_LINE_LENGTH);
        colorParser.parseString(itemText);

        // checking that there were no errors while parsing the string
        if (!colorParser.isSuccessfullyParsed()) {
            event.getHook().sendMessage("Here is a /itemgen command if you want it!\n" + itemGenCommand).setEphemeral(true).queue();
            event.getHook().sendMessage(colorParser.getErrorString()).setEphemeral(true).queue();
            return;
        }

        // creating the minecraft image and sending it to the user.
        MinecraftImage minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, StringColorParser.MAX_FINAL_LINE_LENGTH * 25, 255, 0).render();
        if (minecraftImage != null) {
            event.getHook().sendFiles(FileUpload.fromData(Util.toFile(minecraftImage.getImage()))).setEphemeral(false).queue();
        }

        event.getHook().sendMessage("Here is a /itemgen command if you want it!\n" + itemGenCommand).setEphemeral(true).queue();
    }


    @JDASlashCommand(name = "infogen", description = "Get a little bit of help with how to use the Generator bot.")
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
            .addField("Basic Info", "This is a bot used to create custom items to be used in suggestions. You can use the bot with `/itemgen`, `/headgen`, and `/fullgen`.", true);

        argumentBuilder.setColor(Color.GREEN)
            .addField("Arguments",
                """
                `name`: The name of the item. Defaults to the rarity color, unless the rarity is none.
                `rarity`: Takes any SkyBlock rarity. Can be left as NONE.
                `description`: Parses a description, including color codes, bold, italics, and newlines.
                `type`: The type of the item, such as a Sword or Wand. Can be left blank.
                `handle_line_breaks (true/false)`: To be used if you're manually handling line breaks between the description and rarity.
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
                Finally, you can move your text to a newline by typing `\\n`. If you don't want the extra line break at the end, set the `handle_line_breaks` argument to True.
                """, false);

        extraInfoBuilder.setColor(Color.GRAY)
            .addField("Other Information",
                """
                There is another command `/itemgenparse` which can be used to easily convert the display NBT Tag from a Minecraft item into a Generated Image. This display tag should be surrounded with curly brackets with a "Lore" (string array) and "Name" (string) attribute in them
                You can also check out `/infoheadgen` for more information about rendering items next to your creations!
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

    @JDASlashCommand(name = "infoheadgen", description = "Get a little bit of help with how to use the Head Rendering functions of the Generator bot.")
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
            .addField("Basic Info", "The command `/headgen` which will display a rendered Minecraft Head from a Skin (or player) you chose!", true);

        argumentBuilder.setColor(Color.GREEN)
            .addField("Arguments",
               """
               `skin_id:` The skin ID or the player name of the person you wish to grab the skin from. (This is the string written after `http://textures.minecraft.net/texture/...`
               `is_player_head:` set to True if the skin ID is a player's name
               """, false);

        extraInfoBuilder.setColor(Color.GRAY)
            .addField("Other Information",
                """
                If you are feeling extra spicy, you can combine these two elements by using the `/fullgen` command with arguments mentioned previously.
                The item generation bot is maintained by the Bot Contributors. Feel free to tag them with any issues.
                """, false);

        Collection<MessageEmbed> embeds = new ArrayList<>();
        embeds.add(infoBuilder.build());
        embeds.add(argumentBuilder.build());
        embeds.add(extraInfoBuilder.build());

        event.replyEmbeds(embeds).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "statsymbols", description = "Show a list of all stats symbols")
    public void showAllStats(GuildSlashEvent event) {
        StringBuilder builder = new StringBuilder();

        builder.append("Stats:\n```");
        for (Stat stat : Stat.VALUES) {
            if (stat.name().startsWith("ITEM_STAT")) {
                continue;
            }
            int length = 25 - stat.toString().length();
            builder.append(stat).append(": ").append(" ".repeat(length)).append(stat.getDisplay()).append("\n");
        }

        builder.append("```");
        event.reply(builder.toString()).setEphemeral(true).queue();
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
            //The top channel in the config should be considered the 'primary channel', which is referenced in the
            //error message.
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

    private MinecraftImage buildItem(GuildSlashEvent event, String name, String rarity, String description, String type,
                                    Boolean handleLineBreaks, Integer alpha, Integer padding, Integer maxLineLength) {
        // Checking that the fonts have been loaded correctly
        if (!MinecraftImage.isFontsRegistered()) {
            event.getHook().sendMessage("It seems that one of the font files couldn't be loaded correctly. Please contact a Bot Developer to have a look at it!").setEphemeral(true).queue();
            return null;
        }

        // verify rarity argument
        String finalRarity = rarity;
        if (Arrays.stream(Rarity.VALUES).noneMatch(rarity1 -> finalRarity.equalsIgnoreCase(rarity1.name()))) {
            rarity = rarity.replaceAll("[^a-zA-Z0-9_ ]", "");
            StringBuilder failedRarity = new StringBuilder("You used an invalid rarity, `" + rarity + "`. Valid rarities:\n");
            Arrays.stream(Rarity.VALUES).forEachOrdered(rarity1 -> failedRarity.append(rarity1.name()).append(" "));
            event.getHook().sendMessage(failedRarity.toString()).setEphemeral(true).queue();
            return null;
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

        MinecraftImage minecraftImage = new MinecraftImage(colorParser.getParsedDescription(), MCColor.GRAY, maxLineLength * 25, alpha, padding).render();

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
            event.getHook().sendMessage("Hey, a small heads up - you don't need to include the full URL! Only the skin ID is required")
                    .setEphemeral(true).queue();
        }

        BufferedImage skin;
        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureID);
            skin = ImageIO.read(target);
        } catch (MalformedURLException e) {
            event.getHook().sendMessage("Hey, you kinda got this url wrong... ").setEphemeral(false).queue();
            return null;
        } catch (IOException e) {
            textureID = textureID.replaceAll("[^a-zA-Z0-9_ ]", "");
            event.getHook().sendMessage("It seems that the URL you entered in doesn't link to anything...\nEntered URL: `http://textures.minecraft.net/texture/" + textureID + "`").setEphemeral(false).queue();
            return null;
        }

        return new MinecraftHead(skin).drawHead();
    }

    private String getPlayerHeadURL(GuildSlashEvent event, String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_ ]", "");

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage("There was an error trying to send a request to get the UUID of this player...").queue();
            return null;
        }

        if (userUUID == null || userUUID.get("id") == null) {
            event.getHook().sendMessage("It seems that there is no one with the name `" + playerName + "`").queue();
            return null;
        }

        JsonObject userProfile;
        try {
            userProfile = Util.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage("There was an error trying to send a request to get the UUID of this player...").queue();
            return null;
        }

        if (userProfile == null || userProfile.get("properties") == null) {
            event.getHook().sendMessage("There was a weird issue when trying to get the profile data for `" + playerName + "`").queue();
            return null;
        }

        String base64SkinData = userProfile.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);

        String finalSkinID = skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString();
        finalSkinID = finalSkinID.replace("http://textures.minecraft.net/texture/", "");

        return finalSkinID;
    }
}

