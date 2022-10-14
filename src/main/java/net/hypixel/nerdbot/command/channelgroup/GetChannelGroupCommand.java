package net.hypixel.nerdbot.command.channelgroup;

import net.aerh.jdacommands.command.CommandArgument;
import net.aerh.jdacommands.command.HasArguments;
import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.database.Database;

import java.util.List;

public class GetChannelGroupCommand implements SlashCommand, RequiresPermission, HasArguments {

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "getchannelgroup";
    }

    @Override
    public String getDescription() {
        return "Get a channel group from the database";
    }

    @Override
    public List<CommandArgument> getArguments() {
        return List.of(
                CommandArgument.of(OptionType.STRING, "name", "The name of the ChannelGroup", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            NerdBotApp.LOGGER.error("Couldn't connect to the database!");
            return;
        }

        String name = event.getOption("name").getAsString();
        ChannelGroup channelGroup = Database.getInstance().getChannelGroup(name);
        if (channelGroup == null) {
            event.reply("Couldn't find channel group " + name + " in the database!").setEphemeral(true).queue();
            return;
        }

        event.reply("Channel group " + name + ": " + channelGroup.getFrom() + " -> " + channelGroup.getTo()).setEphemeral(true).queue();
    }
}
