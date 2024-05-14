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
import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.bot.config.objects.PingableRole;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.Util;

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
            Role discordRole = event.getGuild().getRoleById(pingableRole.getRoleId());
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
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(PingableRole::getName)
            .toList();
    }

    @JDASlashCommand(name = "roles", description = "List all roles that can be assigned")
    public void listRoles(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        String roles = Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(PingableRole::getName)
            .collect(Collectors.joining("\n"));

        TranslationManager.edit(event.getHook(), user, "commands.role.list_roles", roles);
    }

    @JDASlashCommand(name = "promotion", description = "Check if you are eligible for a promotion to a higher role")
    public void checkForPromotionEligibility(GuildSlashEvent event) {
        event.deferReply(true).complete();

        RoleConfig roleConfig = NerdBotApp.getBot().getConfig().getRoleConfig();
        if (!roleConfig.isCurrentlyPromotingUsers()) {
            TranslationManager.edit(event.getHook(), "commands.role.not_currently_accepting_promotions");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = repository.findById(event.getMember().getId());

        if (!RoleManager.getHighestRole(event.getMember()).equals(RoleManager.getRoleById(roleConfig.getMemberRoleId()).orElseThrow())) {
            TranslationManager.edit(event.getHook(), user, "commands.role.cannot_progress_further");
            return;
        }

        if (isEligibleForPromotion(user)) {
            TranslationManager.edit(event.getHook(), user, "commands.role.eligible_promotion");
        } else {
            TranslationManager.edit(event.getHook(), user, "commands.role.not_eligible_promotion",
                Util.COMMA_SEPARATED_FORMAT.format(user.getLastActivity().getTotalVotes()),
                Util.COMMA_SEPARATED_FORMAT.format(NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion())
            );
        }
    }

    private boolean isEligibleForPromotion(DiscordUser user) {
        return user.getLastActivity().getTotalVotes() >= NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion();
    }
}
