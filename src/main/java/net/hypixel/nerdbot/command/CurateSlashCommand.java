package net.hypixel.nerdbot.command;

import net.aerh.jdacommands.command.CommandArgument;
import net.aerh.jdacommands.command.HasArguments;
import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;
import java.util.Objects;

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
                CommandArgument.of(OptionType.CHANNEL, "forum", "The forum channel to curate", true),
                CommandArgument.of(OptionType.BOOLEAN, "readonly", "Whether this run should be done in read-only mode", false)
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

        boolean readOnly = false;
        if (event.getOption("readonly") != null) {
            readOnly = Objects.requireNonNull(event.getOption("readonly")).getAsBoolean();
        }

        Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(readOnly);
        ForumChannel forumChannel = event.getOption("forum").getAsChannel().asForumChannel();

        NerdBotApp.EXECUTOR_SERVICE.submit(() -> {
            event.deferReply(true).queue();

            List<GreenlitMessage> output = forumChannelCurator.curate(List.of(forumChannel));
            if (output.isEmpty()) {
                event.getHook().editOriginal("No suggestions were greenlit!").queue();
            } else {
                event.getHook().editOriginal("Greenlit " + output.size() + " suggestions in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!").queue();
            }
        });
    }
}
