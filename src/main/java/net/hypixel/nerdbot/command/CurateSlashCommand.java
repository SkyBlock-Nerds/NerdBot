package net.hypixel.nerdbot.command;

import net.aerh.jdacommands.command.CommandArgument;
import net.aerh.jdacommands.command.HasArguments;
import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public class CurateSlashCommand implements SlashCommand, RequiresPermission, HasArguments {

    @Override
    public String getCommandName() {
        return "curate";
    }

    @Override
    public String getDescription() {
        return "Manually start the suggestion curation process";
    }

    @Override
    public List<CommandArgument> getArguments() {
        return List.of(
                CommandArgument.of(OptionType.STRING, "group", "The ChannelGroup to curate", false),
                CommandArgument.of(OptionType.INTEGER, "amount", "The amount of suggestions to curate", false)
        );
    }

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            Logger.error("Couldn't connect to the database!");
            return;
        }

        int amount = 25;
        if (event.getOption("amount") != null) {
            amount = Math.min(event.getOption("amount").getAsInt(), 200);
        }

        String group = "DefaultSuggestions";
        if (event.getOption("group") != null) {
            group = event.getOption("group").getAsString();
        }

        Curator curator = new Curator(amount, Database.getInstance().getChannelGroup(group));
        NerdBotApp.EXECUTOR_SERVICE.submit(curator::curate);

        if (!group.equals("DefaultSuggestions")) {
            event.reply("Curation started for " + group + " with " + amount + " suggestions").setEphemeral(true).queue();
        } else {
            event.reply("Curation started with " + amount + " suggestions").setEphemeral(true).queue();
        }
    }
}
