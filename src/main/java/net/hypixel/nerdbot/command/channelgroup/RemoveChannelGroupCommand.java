package net.hypixel.nerdbot.command.channelgroup;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.api.channel.ChannelGroup;
import net.hypixel.nerdbot.api.command.slash.CommandArgument;
import net.hypixel.nerdbot.api.command.slash.RestrictedSlashCommand;
import net.hypixel.nerdbot.api.command.slash.SlashCommand;
import net.hypixel.nerdbot.api.command.slash.SlashCommandArguments;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public class RemoveChannelGroupCommand implements SlashCommand, RestrictedSlashCommand, SlashCommandArguments {

    @Override
    public DefaultMemberPermissions getPermission() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "removechannelgroup";
    }

    @Override
    public String getDescription() {
        return "Remove a channel group from the database";
    }

    @Override
    public List<CommandArgument> getArgs() {
        return List.of(
                CommandArgument.of(OptionType.STRING, "name", "The name of the ChannelGroup", true)
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
        ChannelGroup channelGroup = Database.getInstance().getChannelGroup(name);
        if (channelGroup == null) {
            event.reply("Couldn't find channel group " + name + " in the database!").setEphemeral(true).queue();
            return;
        }

        Database.getInstance().deleteChannelGroup(channelGroup);
        event.reply("You deleted channel group `" + channelGroup.getName() + "`!").setEphemeral(true).queue();
    }
}
