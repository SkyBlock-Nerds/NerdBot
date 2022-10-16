package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;

import java.util.List;

public class ChannelGroupCommand extends ApplicationCommand {

    @JDASlashCommand(name = "channelgroup", subcommand = "show", description = "View all channel groups", defaultLocked = true)
    public void showChannelGroups(GuildSlashEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        List<ChannelGroup> channelGroups = Database.getInstance().getChannelGroups();
        if (channelGroups.isEmpty()) {
            event.reply("Couldn't find any channel groups in the database!").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (ChannelGroup channelGroup : channelGroups) {
            sb.append(" • ").append(channelGroup.getName()).append("\n");
        }

        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "channelgroup", subcommand = "add", description = "Create and save a channel group to the database", defaultLocked = true)
    public void addChannelGroup(
            GuildSlashEvent event,
            @AppOption(description = "The name of the channel group") String name,
            @AppOption(description = "The channel to watch for suggestions") TextChannel from,
            @AppOption(description = "The channel to send greenlit suggestions to") TextChannel to
    ) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = new ChannelGroup(name, event.getGuild().getId(), from.getId(), to.getId());
        Database.getInstance().insertChannelGroup(channelGroup);
        event.reply("Added channel group " + name + " to the database!").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "channelgroup", subcommand = "get", description = "View information on a specific channel group", defaultLocked = true)
    public void getChannelGroup(GuildSlashEvent event, @AppOption(description = "The name of the channel group") String name) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = Database.getInstance().getChannelGroup(name);
        if (channelGroup == null) {
            event.reply("Couldn't find channel group " + name + " in the database!").setEphemeral(true).queue();
            return;
        }

        event.reply("Channel group " + name + ": " + channelGroup.getFrom() + " -> " + channelGroup.getTo()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "channelgroup", subcommand = "delete", description = "Delete a channel groups", defaultLocked = true)
    public void removeChannelGroup(GuildSlashEvent event, @AppOption(description = "The name of the channel group") String name) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = Database.getInstance().getChannelGroup(name);
        if (channelGroup == null) {
            event.reply("Couldn't find a channel group named `" + name + "` in the database!").setEphemeral(true).queue();
            return;
        }

        Database.getInstance().deleteChannelGroup(channelGroup);
        event.reply("You deleted channel group `" + channelGroup.getName() + "`!").setEphemeral(true).queue();
    }
}
