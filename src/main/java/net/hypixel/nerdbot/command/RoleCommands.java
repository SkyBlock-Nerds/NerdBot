package net.hypixel.nerdbot.command;

import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;

import net.hypixel.nerdbot.bot.config.RoleConfig;
import net.hypixel.nerdbot.bot.config.objects.PingableRole;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RoleCommands {

    @SlashCommand(name = "role", description = "Toggle a role", guildOnly = true)
    public void toggleRole(SlashCommandInteractionEvent event, @SlashOption(autocompleteId = "pingable-roles") String role) {
        event.deferReply().setEphemeral(true).complete();

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        repository.findOrCreateByIdAsync(event.getUser().getId())
            .thenAccept(user -> {
                RoleManager.getPingableRoleByName(role).ifPresentOrElse(pingableRole -> {
                    Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
                    if (discordRole == null) {
                        event.getHook().editOriginal(String.format("Could not find a role with ID `%s`!", role)).queue();
                        return;
                    }

                    Member member = event.getMember();
                    if (RoleManager.hasRoleByName(member, role)) {
                        event.getGuild().removeRoleFromMember(member, discordRole).queue();
                        event.getHook().editOriginal(String.format("Removed role %s from you! Use the same command to add it back.", discordRole.getAsMention())).queue();
                    } else {
                        event.getGuild().addRoleToMember(member, discordRole).queue();
                        event.getHook().editOriginal(String.format("Added role %s to you! Use the same command to remove it.", discordRole.getAsMention())).queue();
                    }
                }, () -> event.getHook().editOriginal(String.format("Could not find a role with ID `%s`!", role)).queue());
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for role toggle", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    @SlashAutocompleteHandler(id = "pingable-roles")
    public List<Command.Choice> listPingableRoles(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .map(role -> new Command.Choice(role.name(), role.name()))
            .toList();
    }

    @SlashCommand(name = "roles", description = "List all roles that can be assigned", guildOnly = true)
    public void listRoles(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        repository.findByIdAsync(event.getMember().getId())
            .thenAccept(user -> {
                if (user == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                String roles = Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
                    .map(PingableRole::name)
                    .collect(Collectors.joining("\n"));

                event.getHook().editOriginal(String.format("**Assignable Roles:**\n%s\n\n**Use /role <name> to toggle the role!**", roles)).queue();
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for role list", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    @SlashCommand(name = "promotion", description = "Check if you are eligible for a promotion to a higher role", guildOnly = true)
    public void checkForPromotionEligibility(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        RoleConfig roleConfig = NerdBotApp.getBot().getConfig().getRoleConfig();
        if (!roleConfig.isCurrentlyPromotingUsers()) {
            event.getHook().editOriginal("We are not currently assessing promotion eligibility, please check back later!").queue();
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        repository.findByIdAsync(event.getMember().getId())
            .thenAccept(user -> {
                if (user == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                if (!RoleManager.getHighestRole(event.getMember()).equals(RoleManager.getRoleById(roleConfig.getMemberRoleId()).orElseThrow())) {
                    event.getHook().editOriginal("You are already at the highest role!").queue();
                    return;
                }

                if (isEligibleForPromotion(user)) {
                    event.getHook().editOriginal("You are currently eligible for a promotion! This is not a guarantee that you will be promoted. Thanks for contributing to SkyBlock Nerds!").queue();
                } else {
                    event.getHook().editOriginal(String.format("You have %s/%s required suggestion votes and %s/%s required suggestion comments to be nominated for the Orange role!",
                        StringUtils.COMMA_SEPARATED_FORMAT.format(user.getLastActivity().getTotalVotes(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory())),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion()),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(user.getLastActivity().getTotalComments(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory())),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumCommentsRequiredForPromotion())
                    )).queue();
                }
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for promotion check", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    private boolean isEligibleForPromotion(DiscordUser user) {
        return user.getLastActivity().getTotalVotes(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory()) >= NerdBotApp.getBot().getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion();
    }
}
