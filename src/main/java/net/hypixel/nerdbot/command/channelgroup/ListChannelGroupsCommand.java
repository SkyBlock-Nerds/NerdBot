package net.hypixel.nerdbot.command.channelgroup;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.command.slash.RestrictedSlashCommand;
import net.hypixel.nerdbot.api.command.slash.SlashCommand;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public class ListChannelGroupsCommand implements SlashCommand, RestrictedSlashCommand {

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "listchannelgroups";
    }

    @Override
    public String getDescription() {
        return "List all available channel groups";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            Logger.error("Couldn't connect to the database!");
            return;
        }

        List<ChannelGroup> channelGroups = Database.getInstance().getChannelGroups();
        if (channelGroups.isEmpty()) {
            event.reply("Couldn't find any channel groups in the database!").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**All channel groups:**\n\n");
        for (ChannelGroup channelGroup : channelGroups) {
            sb.append(channelGroup.getName()).append("\n");
        }

        event.reply(sb.toString()).setEphemeral(true).queue();
    }
}
