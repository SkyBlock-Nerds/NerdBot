package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.role.PingableRole;
import net.hypixel.nerdbot.role.RoleManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RoleCommands extends ApplicationCommand {

    @JDASlashCommand(name = "role", description = "Toggle a role")
    public void toggleRole(GuildSlashEvent event, @AppOption(autocomplete = "pingable-roles") String role) {
        event.deferReply().setEphemeral(true).complete();

        RoleManager.getPingableRoleByName(role).ifPresentOrElse(pingableRole -> {
            Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
            if (discordRole == null) {
                event.getHook().editOriginal("Invalid role specified!").queue();
                return;
            }

            Member member = event.getMember();
            if (RoleManager.hasRole(member, role)) {
                event.getGuild().removeRoleFromMember(member, discordRole).queue();
                event.getHook().editOriginal("Removed role " + discordRole.getAsMention() + " from you!\nUse the same command to re-add it!").queue();
            } else {
                event.getGuild().addRoleToMember(member, discordRole).queue();
                event.getHook().editOriginal("Added role " + discordRole.getAsMention() + " to you!\nUse the same command to remove it!").queue();
            }
        }, () -> event.getHook().editOriginal("Invalid role specified!").queue());
    }

    @AutocompletionHandler(name = "pingable-roles", showUserInput = false)
    public List<String> listPingableRoles(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles()).map(PingableRole::name).toList();
    }

    @JDASlashCommand(name = "roles", description = "List all roles that can be assigned")
    public void listRoles(GuildSlashEvent event) {
        event.deferReply(true).complete();
        String roles = Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles()).map(PingableRole::name).collect(Collectors.joining("\n"));
        event.getHook().editOriginal("**Assignable Roles:**\n" + roles + "\n\n**Use /role <name> to toggle the role!**").queue();
    }
}
