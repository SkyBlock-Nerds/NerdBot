package net.hypixel.nerdbot.command.channelgroup;

import net.aerh.jdacommands.command.CommandArgument;
import net.aerh.jdacommands.command.HasArguments;
import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public class AddChannelGroupCommand implements SlashCommand, RequiresPermission, HasArguments {

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "addchannelgroup";
    }

    @Override
    public String getDescription() {
        return "Add a channel group to the database";
    }

    @Override
    public List<CommandArgument> getArguments() {
        return List.of(
                CommandArgument.of(OptionType.STRING, "name", "The name of the ChannelGroup", true),
                CommandArgument.of(OptionType.CHANNEL, "from", "The channel to take submissions from", true),
                CommandArgument.of(OptionType.CHANNEL, "to", "The channel to send approved submissions", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            Logger.error("Couldn't connect to the database!");
            return;
        }

        String name = event.getOption("name").getAsString();
        Channel from = event.getOption("from").getAsChannel();
        Channel to = event.getOption("to").getAsChannel();

        ChannelGroup channelGroup = new ChannelGroup(name, event.getGuild().getId(), from.getId(), to.getId());
        Database.getInstance().insertChannelGroup(channelGroup);
        event.reply("Added channel group " + name + " to the database!").setEphemeral(true).queue();
    }
}
