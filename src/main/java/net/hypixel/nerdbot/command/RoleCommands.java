package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.PingableRole;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.wiki.MediaWikiAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Log4j2
public class RoleCommands extends ApplicationCommand {

    @JDASlashCommand(name = "role", description = "Toggle a role")
    public void toggleRole(GuildSlashEvent event, @AppOption(autocomplete = "pingable-roles") String role) {
        event.deferReply().setEphemeral(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to the database!").queue();
            return;
        }

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getMember().getId());

        if (user == null) {
            event.getHook().editOriginal("Could not find you in the database!").queue();
            return;
        }

        RoleManager.getPingableRoleByName(role).ifPresentOrElse(pingableRole -> {
            Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
            if (discordRole == null) {
                event.getHook().editOriginal("Invalid role specified!").queue();
                return;
            }

            // Hardcoded Wiki Editor role check
            if (isWikiRole(discordRole.getId()) && !MediaWikiAPI.isEditor(user.getMojangProfile().getUsername())) {
                event.getHook().editOriginal("You need to be a Wiki Editor to toggle that role!").queue();
                return;
            }

            Member member = event.getMember();
            if (RoleManager.hasRole(member, role)) {
                event.getGuild().removeRoleFromMember(member, discordRole).queue();
                event.getHook().editOriginal("Removed role " + discordRole.getAsMention() + " from you!\nUse the same command to re-add it!").queue();

                if (isWikiRole(discordRole.getId())) {
                    user.setAutoGiveWikiRole(false);
                    log.debug("Will not auto-add Wiki Editor role to " + user.getMojangProfile().getUsername() + " anymore!");
                }
            } else {
                event.getGuild().addRoleToMember(member, discordRole).queue();
                event.getHook().editOriginal("Added role " + discordRole.getAsMention() + " to you!\nUse the same command to remove it!").queue();

                if (isWikiRole(discordRole.getId())) {
                    user.setAutoGiveWikiRole(true);
                    log.debug("Will auto-add Wiki Editor role to " + user.getMojangProfile().getUsername() + "!");
                }
            }
        }, () -> event.getHook().editOriginal("Invalid role specified!").queue());
    }

    @AutocompletionHandler(name = "pingable-roles", showUserInput = false)
    public List<String> listPingableRoles(CommandAutoCompleteInteractionEvent event) {
        List<String> roles = new ArrayList<>(Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(PingableRole::name)
            .toList());

        if (event.getMember() == null) {
            return roles;
        }

        // Remove Wiki Editor role if user is not a Wiki Editor
        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getMember().getId());

        if (user != null && user.isProfileAssigned() && !MediaWikiAPI.isEditor(user.getMojangProfile().getUsername())) {
            roles.remove("Wiki Editor");
        }

        return roles;
    }

    @JDASlashCommand(name = "roles", description = "List all roles that can be assigned")
    public void listRoles(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getMember().getId());

        if (user == null) {
            event.getHook().editOriginal("Could not find you in the database!").queue();
            return;
        }

        if (user.noProfileAssigned()) {
            event.getHook().editOriginal("You need to link your Minecraft account before you can do this!").queue();
            return;
        }

        List<String> roles = new ArrayList<>(Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(PingableRole::name)
            .toList());

        if (!MediaWikiAPI.isEditor(user.getMojangProfile().getUsername())) {
            roles.remove("Wiki Editor");
        }

        event.getHook().editOriginal("**Assignable Roles:**\n" + String.join("\n", roles) + "\n\n**Use /role <name> to toggle the role!**").queue();
    }

    public static boolean isWikiRole(String roleId) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .filter(pingableRole -> pingableRole.name().equalsIgnoreCase("Wiki Editor"))
            .map(pingableRole -> pingableRole.roleId().equals(roleId))
            .findFirst()
            .orElse(false);
    }
}
