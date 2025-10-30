package net.hypixel.nerdbot.discord.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.bot.DiscordBot;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.config.RoleConfig;
import net.hypixel.nerdbot.config.objects.PingableRole;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class RoleCommands {

    @SlashCommand(name = "roles", description = "View and manage your assignable roles", guildOnly = true)
    public void roleMenu(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        if (event.getGuild() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        PingableRole[] pingableRoles = ((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getPingableRoles();
        Member member = event.getMember();

        if (pingableRoles.length == 0) {
            event.getHook().editOriginal("No roles are currently available.").queue();
            return;
        }

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create("role-select-" + member.getId())
            .setPlaceholder("Select a role to toggle")
            .setRequiredRange(1, 1);

        for (PingableRole pingableRole : pingableRoles) {
            Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
            if (discordRole != null) {
                boolean hasRole = RoleManager.hasRoleById(member, pingableRole.roleId());
                String emoji = hasRole ? "✅" : "❌";
                String description = hasRole ? "Currently assigned - click to remove" : "Not assigned - click to add";

                selectBuilder.addOption(emoji + " " + pingableRole.name(), pingableRole.name(), description);
            }
        }

        ActionRow selectRow = ActionRow.of(selectBuilder.build());

        List<String> currentRoles = Arrays.stream(pingableRoles)
            .filter(role -> RoleManager.hasRoleById(member, role.roleId()))
            .map(PingableRole::name)
            .collect(Collectors.toList());

        String statusMessage = currentRoles.isEmpty()
            ? "**Your Current Roles:** None\n\n" +
            "**Available Roles:** " + pingableRoles.length + "\n" +
            "Use the dropdown below to add or remove roles."
            : "**Your Current Roles:** " + String.join(", ", currentRoles) + "\n\n" +
            "**Available Roles:** " + pingableRoles.length + "\n" +
            "Use the dropdown below to add or remove roles.";

        event.getHook().editOriginal(statusMessage)
            .setComponents(selectRow)
            .queue();
    }

    @SlashCommand(name = "promotion", description = "Check if you are eligible for a promotion to a higher role", guildOnly = true)
    public void checkForPromotionEligibility(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        RoleConfig roleConfig = ((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig();
        if (!roleConfig.isCurrentlyPromotingUsers()) {
            event.getHook().editOriginal("We are not currently assessing promotion eligibility, please check back later!").queue();
            return;
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

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
                        StringUtils.COMMA_SEPARATED_FORMAT.format(user.getLastActivity().getTotalVotes(((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getDaysRequiredForVoteHistory())),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion()),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(user.getLastActivity().getTotalComments(((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getDaysRequiredForVoteHistory())),
                        StringUtils.COMMA_SEPARATED_FORMAT.format(((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getMinimumCommentsRequiredForPromotion())
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
        return user.getLastActivity().getTotalVotes(((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getDaysRequiredForVoteHistory()) >= ((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getMinimumVotesRequiredForPromotion();
    }

    @SlashComponentHandler(id = "role-select", patterns = {"role-select-*"})
    public void handleRoleSelect(StringSelectInteractionEvent event) {
        String userId = event.getComponentId().split("-")[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own role menu!").setEphemeral(true).queue();
            return;
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String selectedRole = event.getValues().get(0);
        Member member = event.getMember();

        if (member == null) {
            event.getHook().sendMessage("An error occurred, please try again later.").setEphemeral(true).queue();
            return;
        }

        RoleManager.getPingableRoleByName(selectedRole).ifPresentOrElse(pingableRole -> {
            Role discordRole = event.getGuild().getRoleById(pingableRole.roleId());
            if (discordRole == null) {
                event.getHook().sendMessage("❌ Could not find role: " + selectedRole).setEphemeral(true).queue();
                return;
            }

            boolean hadRole = RoleManager.hasRoleById(member, pingableRole.roleId());

            if (hadRole) {
                event.getGuild().removeRoleFromMember(member, discordRole).queue(success -> {
                    updateRoleMenuInternal(event.getHook(), member, event.getGuild(), "✅ Removed role: " + discordRole.getAsMention());
                }, error -> {
                    event.getHook().sendMessage("❌ Failed to remove role: " + selectedRole).setEphemeral(true).queue();
                });
            } else {
                event.getGuild().addRoleToMember(member, discordRole).queue(success -> {
                    updateRoleMenuInternal(event.getHook(), member, event.getGuild(), "✅ Added role: " + discordRole.getAsMention());
                }, error -> {
                    event.getHook().sendMessage("❌ Failed to add role: " + selectedRole).setEphemeral(true).queue();
                });
            }
        }, () -> {
            event.getHook().sendMessage("❌ Role not found: " + selectedRole).setEphemeral(true).queue();
        });
    }

    private void updateRoleMenuInternal(InteractionHook hook, Member member, Guild guild, String message) {
        PingableRole[] pingableRoles = ((DiscordBot) BotEnvironment.getBot()).getConfig().getRoleConfig().getPingableRoles();

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create("role-select-" + member.getId())
            .setPlaceholder("Select a role to toggle")
            .setRequiredRange(1, 1);

        for (PingableRole pingableRole : pingableRoles) {
            Role discordRole = guild.getRoleById(pingableRole.roleId());
            if (discordRole != null) {
                boolean hasRole = RoleManager.hasRoleById(member, pingableRole.roleId());
                String emoji = hasRole ? "✅" : "❌";
                String description = hasRole ? "Currently assigned - click to remove" : "Not assigned - click to add";

                selectBuilder.addOption(emoji + " " + pingableRole.name(), pingableRole.name(), description);
            }
        }

        ActionRow selectRow = ActionRow.of(selectBuilder.build());

        List<String> currentRoles = Arrays.stream(pingableRoles)
            .filter(role -> RoleManager.hasRoleById(member, role.roleId()))
            .map(PingableRole::name)
            .collect(Collectors.toList());

        String statusMessage = (message != null ? message + "\n\n" : "") +
            (currentRoles.isEmpty()
                ? "**Your Current Roles:** None\n\n" +
                "**Available Roles:** " + pingableRoles.length + "\n" +
                "Use the dropdown below to add or remove roles."
                : "**Your Current Roles:** " + String.join(", ", currentRoles) + "\n\n" +
                "**Available Roles:** " + pingableRoles.length + "\n" +
                "Use the dropdown below to add or remove roles.");

        hook.editOriginal(statusMessage)
            .setComponents(selectRow)
            .queue();
    }
}