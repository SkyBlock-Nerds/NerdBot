package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.RestAction;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.nomination.ActivityRequirements;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.PingableRole;
import net.hypixel.nerdbot.discord.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class RoleCommands {

    static final String SELECT_PREFIX = "role-select";
    static final PanelId SELECT = PanelId.of(SELECT_PREFIX, 1, 0);
    static final String NOT_YOUR_MENU = "You can only use your own role menu!";
    static final String SERVER_ONLY = "This command can only be used in a server!";

    /** Where a member sits relative to the Member role, which is the one /promotion checks from. */
    enum PromotionStanding {
        BELOW_MEMBER,
        MEMBER,
        ABOVE_MEMBER
    }

    /** What the role menu shows: the status text and the select menu, which is absent when no configured role exists. */
    record RoleMenuView(String text, Optional<StringSelectMenu> menu) {
    }

    private final Provider<NerdBotConfig> config;
    private final DiscordUserStore users;

    @Inject
    public RoleCommands(Provider<NerdBotConfig> config, DiscordUserStore users) {
        this.config = config;
        this.users = users;
    }

    @SlashCommand(name = "roles", description = "View and manage your assignable roles", guildOnly = true)
    public void roleMenu(SlashCommandInteractionEvent event) {
        showRoleMenu(event);
    }

    @SlashCommand(name = "promotion", description = "Check if you are eligible for a promotion to a higher role", guildOnly = true)
    public void checkForPromotionEligibility(SlashCommandInteractionEvent event) {
        checkPromotion(event);
    }

    @SlashComponentHandler(id = SELECT_PREFIX, patterns = {SELECT_PREFIX + "-*"})
    public void handleRoleSelect(StringSelectInteractionEvent event) {
        toggleRole(event);
    }

    void showRoleMenu(IReplyCallback interaction) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        Guild guild = interaction.getGuild();
        Member member = interaction.getMember();
        if (guild == null || member == null) {
            hook.editOriginal(SERVER_ONLY).queue();
            return;
        }

        List<PingableRole> roles = pingableRoles();
        RoleMenuView view = roleMenu(member.getId(), roles, roleIds(member), roleId -> guild.getRoleById(roleId) != null, null);
        if (view.menu().isEmpty()) {
            hook.editOriginal("No roles are currently available.").queue();
            return;
        }

        hook.editOriginal(view.text()).setComponents(ActionRow.of(view.menu().get())).queue();
    }

    void toggleRole(StringSelectInteraction interaction) {
        if (SELECT.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_MENU).isEmpty()) {
            return;
        }

        Guild guild = interaction.getGuild();
        Member member = interaction.getMember();
        if (guild == null || member == null) {
            CommandReplies.replyPrivately(interaction, SERVER_ONLY);
            return;
        }

        interaction.deferEdit().queue();
        InteractionHook hook = interaction.getHook();
        String selected = interaction.getValues().isEmpty() ? "" : interaction.getValues().getFirst();

        Optional<PingableRole> pingableRole = pingableRoles().stream()
            .filter(role -> role.name().equalsIgnoreCase(selected))
            .findFirst();
        if (pingableRole.isEmpty()) {
            hook.sendMessage("❌ Role not found: " + selected).setEphemeral(true).queue();
            return;
        }

        Role role = guild.getRoleById(pingableRole.get().roleId());
        if (role == null) {
            hook.sendMessage("❌ Could not find role: " + selected).setEphemeral(true).queue();
            return;
        }

        boolean hadRole = roleIds(member).contains(role.getId());
        RestAction<Void> change = hadRole ? guild.removeRoleFromMember(member, role) : guild.addRoleToMember(member, role);

        change.queue(success -> {
            // The member object can lag behind the change until Discord sends the update, so the menu is drawn from what we just did
            Set<String> heldNow = new HashSet<>(roleIds(member));
            if (hadRole) {
                heldNow.remove(role.getId());
            } else {
                heldNow.add(role.getId());
            }

            String message = (hadRole ? "✅ Removed role: " : "✅ Added role: ") + role.getAsMention();
            RoleMenuView view = roleMenu(member.getId(), pingableRoles(), heldNow, roleId -> guild.getRoleById(roleId) != null, message);
            view.menu().ifPresentOrElse(
                menu -> hook.editOriginal(view.text()).setComponents(ActionRow.of(menu)).queue(),
                () -> hook.editOriginal(view.text()).setComponents().queue());
        }, failure -> {
            log.warn("Failed to {} role {} for member {}", hadRole ? "remove" : "add", role.getId(), member.getId(), failure);
            hook.sendMessage((hadRole ? "❌ Failed to remove role: " : "❌ Failed to add role: ") + selected).setEphemeral(true).queue();
        });
    }

    void checkPromotion(IReplyCallback interaction) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        RoleConfig roleConfig = config.get().getRoleConfig();

        if (!roleConfig.isCurrentlyPromotingUsers()) {
            hook.editOriginal("We are not currently assessing promotion eligibility, please check back later!").queue();
            return;
        }

        Member member = interaction.getMember();
        if (member == null) {
            hook.editOriginal(SERVER_ONLY).queue();
            return;
        }

        if (roleConfig.getMemberRoleId() == null || roleConfig.getMemberRoleId().isBlank()) {
            log.warn("/promotion was used but roleConfig.memberRoleId is not set");
            hook.editOriginal("Promotion checks aren't set up yet, please check back later!").queue();
            return;
        }

        switch (promotionStanding(member.getRoles().stream().map(ISnowflake::getId).toList(), roleConfig)) {
            case BELOW_MEMBER -> {
                hook.editOriginal("Promotion checks start once you have the Member role.").queue();
                return;
            }
            case ABOVE_MEMBER -> {
                hook.editOriginal("You are already at the highest role!").queue();
                return;
            }
            case MEMBER -> {
            }
        }

        ActivityRequirements.Progress progress = ActivityRequirements.forPromotion(roleConfig)
            .measure(users.require(member.getId()).getLastActivity());
        hook.editOriginal(promotionMessage(progress)).queue();
    }

    /**
     * Uses the promotion path ({@code promotionTierRoleIds}, lowest first) to tell members above the
     * Member role from those below it. Without the Member role on that path, it falls back to
     * whether the Member role is their highest role.
     *
     * @param heldRoleIds the member's roles, highest first as Discord orders them
     */
    static PromotionStanding promotionStanding(List<String> heldRoleIds, RoleConfig roleConfig) {
        String memberRoleId = roleConfig.getMemberRoleId();
        boolean holdsMemberRole = heldRoleIds.stream().anyMatch(memberRoleId::equalsIgnoreCase);
        List<String> path = roleConfig.getPromotionTierRoleIds() == null ? List.of() : Arrays.asList(roleConfig.getPromotionTierRoleIds());
        int memberIndex = indexIgnoringCase(path, memberRoleId);

        if (memberIndex >= 0) {
            boolean holdsHigherTier = path.subList(memberIndex + 1, path.size()).stream()
                .anyMatch(tier -> heldRoleIds.stream().anyMatch(tier::equalsIgnoreCase));
            if (holdsHigherTier) {
                return PromotionStanding.ABOVE_MEMBER;
            }
            return holdsMemberRole ? PromotionStanding.MEMBER : PromotionStanding.BELOW_MEMBER;
        }

        if (!holdsMemberRole) {
            return PromotionStanding.BELOW_MEMBER;
        }
        return heldRoleIds.getFirst().equalsIgnoreCase(memberRoleId) ? PromotionStanding.MEMBER : PromotionStanding.ABOVE_MEMBER;
    }

    /** Eligibility is on votes alone; comments are shown as they count towards the nomination. */
    static String promotionMessage(ActivityRequirements.Progress progress) {
        if (progress.votesMet()) {
            return "You are currently eligible for a promotion! This is not a guarantee that you will be promoted. Thanks for contributing to SkyBlock Nerds!";
        }

        return String.format("You have %s/%s required suggestion votes and %s/%s required suggestion comments to be nominated for the Orange role!",
            StringUtils.formatWithCommas(progress.votes()),
            StringUtils.formatWithCommas(progress.requirements().requiredVotes()),
            StringUtils.formatWithCommas(progress.comments()),
            StringUtils.formatWithCommas(progress.requirements().requiredComments()));
    }

    static RoleMenuView roleMenu(String memberId, List<PingableRole> roles, Set<String> heldRoleIds, Predicate<String> roleExists, @Nullable String message) {
        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(SELECT.build(memberId))
            .setPlaceholder("Select a role to toggle")
            .setRequiredRange(1, 1);

        for (PingableRole role : roles) {
            if (!roleExists.test(role.roleId())) {
                continue;
            }

            boolean held = heldRoleIds.contains(role.roleId());
            selectBuilder.addOption((held ? "✅ " : "❌ ") + role.name(), role.name(),
                held ? "Currently assigned - click to remove" : "Not assigned - click to add");
        }

        String current = roles.stream()
            .filter(role -> heldRoleIds.contains(role.roleId()))
            .map(PingableRole::name)
            .collect(Collectors.joining(", "));

        String text = (message != null ? message + "\n\n" : "")
            + "**Your Current Roles:** " + (current.isEmpty() ? "None" : current) + "\n\n"
            + "**Available Roles:** " + roles.size() + "\n"
            + "Use the dropdown below to add or remove roles.";

        return new RoleMenuView(text, selectBuilder.getOptions().isEmpty() ? Optional.empty() : Optional.of(selectBuilder.build()));
    }

    private List<PingableRole> pingableRoles() {
        PingableRole[] roles = config.get().getRoleConfig().getPingableRoles();
        return roles == null ? List.of() : List.of(roles);
    }

    private static Set<String> roleIds(Member member) {
        return member.getRoles().stream().map(ISnowflake::getId).collect(Collectors.toSet());
    }

    private static int indexIgnoringCase(List<String> values, String wanted) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null && values.get(i).equalsIgnoreCase(wanted)) {
                return i;
            }
        }
        return -1;
    }
}
