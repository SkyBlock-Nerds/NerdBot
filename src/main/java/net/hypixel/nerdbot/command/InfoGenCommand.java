package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.ChannelManager;
import net.hypixel.nerdbot.generator.MinecraftImage;
import net.hypixel.nerdbot.generator.StringColorParser;
import net.hypixel.nerdbot.util.skyblock.MCColor;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class InfoGenCommand extends ApplicationCommand {
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
        builder.append("`handle_line_breaks- (true/false)`: To be used if you're manually handling line breaks between the description and rarity.\n\n");
        builder.append("The Item Generator bot also accepts color codes. You can use these with either manual Minecraft codes, such as `&1`, or `%%DARK_BLUE%%`.\n");
        builder.append("You can use this same format for stats, such as `%%PRISTINE%%`. \nThis format can also have numbers, where `%%PRISTINE:1%%` will become \"1 ✧ Pristine\"\n");
        builder.append("Finally, you can move your text to a newline by using \\n. This format can be forced with the handle_line_breaks argument.\n\n");
        builder.append("Have fun making items! You can click the blue /itemgen command above anyone's image to see what command they're using to create their image. Thanks!\n\n");
        builder.append("The item generation bot is maintained by mrkeith. Feel free to tag him with any issues.");


        event.reply(builder.toString()).setEphemeral(true).queue();
    }
}
