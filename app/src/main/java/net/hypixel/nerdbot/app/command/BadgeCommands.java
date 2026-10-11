package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.badge.BadgeCatalog;
import net.hypixel.nerdbot.app.badge.BadgeService;
import net.hypixel.nerdbot.app.badge.BadgeService.Change;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
public class BadgeCommands {

    /** Longer lists are sent as a file. */
    static final int MAX_INLINE_LIST_LENGTH = 2_000;

    private final BadgeService badgeService;
    private final BadgeCatalog badges;

    @Inject
    public BadgeCommands(BadgeService badgeService, BadgeCatalog badges) {
        this.badgeService = badgeService;
        this.badges = badges;
    }

    @SlashCommand(name = "badge", subcommand = "give", description = "Award a badge to a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeAward(SlashCommandInteractionEvent event, @SlashOption Member member, @SlashOption(autocompleteId = "available_badges") String badgeId, @SlashOption(required = false) int tier) {
        give(event, member, badgeId, tier);
    }

    @SlashCommand(name = "badge", subcommand = "revoke", description = "Remove a badge from a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeRemove(SlashCommandInteractionEvent event, @SlashOption Member member, @SlashOption(autocompleteId = "available_badges") String badgeId, @SlashOption(required = false) int tier) {
        revoke(event, member, badgeId, tier);
    }

    @SlashCommand(name = "badge", subcommand = "list", description = "List all available badges", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeList(SlashCommandInteractionEvent event) {
        list(event);
    }

    @SlashAutocompleteHandler(id = "available_badges")
    public List<Command.Choice> getAvailableBadges(CommandAutoCompleteInteractionEvent event) {
        return matchingBadgeChoices(badges.all(), event.getFocusedOption().getValue());
    }

    void give(IReplyCallback interaction, Member member, String badgeId, int tier) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        String moderator = interaction.getUser().getId();

        CommandReplies.finish(badgeService.give(member.getId(), badgeId, tier), hook,
            "give badge " + badgeId + " to " + member.getId(), "Failed to save the badge change. Please try again.", change -> {
                hook.editOriginal(describe(change, member.getEffectiveName())).queue();
                if (change instanceof Change.Given given) {
                    log.info("{} gave {} badge '{}' tier {}", moderator, member.getId(), given.badge().getId(), given.tier());
                }
            });
    }

    void revoke(IReplyCallback interaction, Member member, String badgeId, int tier) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        String moderator = interaction.getUser().getId();

        CommandReplies.finish(badgeService.revoke(member.getId(), badgeId, tier), hook,
            "revoke badge " + badgeId + " from " + member.getId(), "Failed to save the badge change. Please try again.", change -> {
                hook.editOriginal(describe(change, member.getEffectiveName())).queue();
                if (change instanceof Change.Revoked revoked) {
                    log.info("{} revoked badge '{}' tier {} from {}", moderator, revoked.badge().getId(), revoked.tier(), member.getId());
                }
            });
    }

    void list(IReplyCallback interaction) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        String text = listText(badges.all());

        if (text.length() > MAX_INLINE_LIST_LENGTH) {
            hook.editOriginal(MessageEditData.fromFiles(FileUpload.fromData(text.getBytes(StandardCharsets.UTF_8), "badges.txt"))).queue();
            return;
        }

        hook.editOriginal(text).queue();
    }

    static String describe(Change change, String memberName) {
        return switch (change) {
            case Change.Given(Badge badge, int tier) -> tier == 0
                ? "Gave badge " + badge.getName() + " to " + memberName + "!"
                : "Gave Tier " + tier + " of the " + badge.getName() + " badge to " + memberName + "!";
            case Change.Revoked(Badge badge, int tier) -> tier == 0
                ? "Removed the " + badge.getName() + " badge from " + memberName + "!"
                : "Removed Tier " + tier + " of the " + badge.getName() + " badge from " + memberName + "!";
            case Change.UnknownBadge(String badgeId) -> badgeId + " is an invalid Badge ID!";
            case Change.InvalidTier(Badge badge, int tiers) -> badge.getName() + " is an invalid Badge Tier! It only has " + tiers + " tiers.";
            case Change.AlreadyHeld(Badge badge, int tier) -> tier == 0
                ? memberName + " already has the " + badge.getName() + " badge!"
                : memberName + " already has Tier " + tier + " of the " + badge.getName() + " badge!";
            case Change.NotHeld(Badge badge, int tier) -> tier == 0
                ? memberName + " does not have the " + badge.getName() + " badge!"
                : memberName + " does not have Tier " + tier + " of the " + badge.getName() + " badge!";
        };
    }

    /** Sorted by id so the list reads the same every time. */
    static String listText(Map<String, Badge> all) {
        StringBuilder text = new StringBuilder("Available Badges (" + all.size() + "):\n");

        new TreeMap<>(all).forEach((id, badge) -> {
            text.append("- ").append(badge.getEmoji() != null ? badge.getFormattedName() : badge.getName())
                .append(" (").append(id).append(")\n");

            if (badge instanceof TieredBadge tieredBadge) {
                tieredBadge.getTiers().forEach(tier -> text.append("  - ").append(tier.getFormattedName())
                    .append(" (tier: ").append(tier.getTier()).append(")\n"));
            }
        });

        return text.toString();
    }

    static List<Command.Choice> matchingBadgeChoices(Map<String, Badge> badges, String typed) {
        String query = typed.toLowerCase(Locale.ROOT);

        return badges.entrySet().stream()
            .map(entry -> new Command.Choice(entry.getValue().getName(), entry.getKey()))
            .filter(choice -> choice.getName().toLowerCase(Locale.ROOT).contains(query))
            .sorted(Comparator.comparing(Command.Choice::getName))
            .limit(OptionData.MAX_CHOICES)
            .toList();
    }
}
