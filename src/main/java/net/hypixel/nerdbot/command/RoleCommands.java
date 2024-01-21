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
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.PingableRole;
import net.hypixel.nerdbot.role.RoleManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RoleCommands extends ApplicationCommand {

    @JDASlashCommand(name = "role", description = "Toggle a role")
    public void toggleRole(GuildSlashEvent event, @AppOption(autocomplete = "pingable-roles") String role) {
        event.deferReply().setEphemeral(true).complete();

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findOrCreateById(event.getUser().getId());

        RoleManager.getPingableRoleByName(role).ifPresentOrElse(pingableRole -> {
            Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
            if (discordRole == null) {
                TranslationManager.edit(event.getHook(), user, "commands.role.invalid_role");
                return;
            }

            Member member = event.getMember();
            if (RoleManager.hasRole(member, role)) {
                event.getGuild().removeRoleFromMember(member, discordRole).queue();
                TranslationManager.edit(event.getHook(), user, "commands.role.removed_role", discordRole.getAsMention());
            } else {
                event.getGuild().addRoleToMember(member, discordRole).queue();
                TranslationManager.edit(event.getHook(), user, "commands.role.added_role", discordRole.getAsMention());
            }
        }, () -> TranslationManager.edit(event.getHook(), user, "commands.role.invalid_role", role));
    }

    @AutocompletionHandler(name = "pingable-roles", showUserInput = false)
    public List<String> listPingableRoles(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles()).map(PingableRole::name).toList();
    }

    @JDASlashCommand(name = "roles", description = "List all roles that can be assigned")
    public void listRoles(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        String roles = Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(PingableRole::name)
            .collect(Collectors.joining("\n"));

        TranslationManager.edit(event.getHook(), user, "commands.role.list_roles", roles);
    }
}
