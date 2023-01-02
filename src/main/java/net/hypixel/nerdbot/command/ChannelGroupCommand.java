package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.mongodb.client.MongoCollection;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ChannelGroupCommand extends ApplicationCommand {

    private final Database database = NerdBotApp.getBot().getDatabase();
    private final MongoCollection<ChannelGroup> channelGroupCollection = database.getCollection("channel_groups", ChannelGroup.class);

    @JDASlashCommand(name = "channelgroup", subcommand = "show", description = "View all channel groups", defaultLocked = true)
    public void showChannelGroups(GuildSlashEvent event) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        List<ChannelGroup> channelGroups = channelGroupCollection.find().into(new ArrayList<>());
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
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = new ChannelGroup(name, event.getGuild().getId(), from.getId(), to.getId());
        channelGroupCollection.insertOne(channelGroup);
        event.reply("Added channel group " + name + " to the database!").setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "channelgroup", subcommand = "get", description = "View information on a specific channel group", defaultLocked = true)
    public void getChannelGroup(GuildSlashEvent event, @AppOption(description = "The name of the channel group") String name) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = database.findDocument(channelGroupCollection, "name", name).first();
        if (channelGroup == null) {
            event.reply("Couldn't find channel group " + name + " in the database!").setEphemeral(true).queue();
            return;
        }

        event.reply("Channel group " + name + ": " + channelGroup.getFrom() + " -> " + channelGroup.getTo()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "channelgroup", subcommand = "delete", description = "Delete a channel groups", defaultLocked = true)
    public void removeChannelGroup(GuildSlashEvent event, @AppOption(description = "The name of the channel group") String name) {
        if (!database.isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        ChannelGroup channelGroup = database.findDocument(channelGroupCollection, "name", name).first();
        if (channelGroup == null) {
            event.reply("Couldn't find a channel group named `" + name + "` in the database!").setEphemeral(true).queue();
            return;
        }

        database.deleteDocument(channelGroupCollection, "name", channelGroup.getName());
        event.reply("You deleted channel group `" + channelGroup.getName() + "`!").setEphemeral(true).queue();
    }
}
