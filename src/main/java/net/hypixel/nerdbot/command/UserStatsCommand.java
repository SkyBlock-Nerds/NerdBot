package net.hypixel.nerdbot.command;

import net.aerh.jdacommands.command.CommandArgument;
import net.aerh.jdacommands.command.HasArguments;
import net.aerh.jdacommands.command.RequiresPermission;
import net.aerh.jdacommands.command.SlashCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.DiscordUser;

import java.util.List;

public class UserStatsCommand implements SlashCommand, RequiresPermission, HasArguments {

    @Override
    public DefaultMemberPermissions permissionRequired() {
        return DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS);
    }

    @Override
    public String getCommandName() {
        return "userstats";
    }

    @Override
    public String getDescription() {
        return "Display a user's reaction stats";
    }

    @Override
    public List<CommandArgument> getArguments() {
        return List.of(
                CommandArgument.of(OptionType.MENTIONABLE, "user", "The user to display stats for", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Database.getInstance().isConnected()) {
            event.reply("Couldn't connect to the database!").setEphemeral(true).queue();
            return;
        }

        User user = event.getOption("user").getAsUser();
        DiscordUser discordUser = Database.getInstance().getUser(user.getId());

        if (discordUser == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Stats for ").append(user.getAsMention()).append("**\n");
        sb.append("**Total agrees:** ").append(discordUser.getAgrees().size()).append("\n");
        sb.append("**Total disagrees:** ").append(discordUser.getDisagrees().size()).append("\n");
        sb.append("**Last known activity date:** ").append(discordUser.getLastKnownActivityDate()).append("\n");

        event.reply(sb.toString()).setEphemeral(true).queue();
    }
}
