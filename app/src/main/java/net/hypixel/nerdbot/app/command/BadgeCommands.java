package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.io.IOException;
import java.util.List;

@Slf4j
public class BadgeCommands {

    @SlashCommand(name = "badge", subcommand = "give", description = "Award a badge to a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeAward(SlashCommandInteractionEvent event, @SlashOption Member member, @SlashOption(autocompleteId = "available_badges") String badgeId, @SlashOption(required = false) int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                Badge badge = BadgeManager.getBadgeById(badgeId);

                if (badge == null) {
                    event.getHook().editOriginal(String.format("%s is an invalid Badge ID!", badgeId)).queue();
                    return;
                }

                if (badge instanceof TieredBadge tieredBadge) {
                    int finalTier = tier < 1 ? 1 : tier;

                    if (finalTier > tieredBadge.getTiers().size()) {
                        event.getHook().editOriginal(String.format("%s is an invalid Badge Tier! It only has %d tiers.", badge.getName(), tieredBadge.getTiers().size())).queue();
                        return;
                    }

                    if (discordUser.addBadge(tieredBadge, finalTier)) {
                        event.getHook().editOriginal(String.format("Gave Tier %d of the %s badge to %s!", finalTier, badge.getName(), member.getEffectiveName())).queue();
                        log.info("{} gave {} tier {} of badge '{}' (ID: {})", event.getMember().getEffectiveName(), member.getEffectiveName(), finalTier, badge.getName(), badge.getId());
                    } else {
                        event.getHook().editOriginal(String.format("%s already has Tier %d of the %s badge!", member.getEffectiveName(), finalTier, badge.getName())).queue();
                    }
                } else {
                    if (discordUser.hasBadge(badge)) {
                        event.getHook().editOriginal(String.format("%s already has the %s badge!", member.getEffectiveName(), badge.getName())).queue();
                        return;
                    }

                    if (discordUser.addBadge(badge)) {
                        event.getHook().editOriginal(String.format("Gave badge %s to %s!", badge.getName(), member.getEffectiveName())).queue();
                        log.info("{} gave {} badge '{}' (ID: {})", event.getMember().getEffectiveName(), member.getEffectiveName(), badge.getName(), badge.getId());
                    } else {
                        event.getHook().editOriginal(String.format("Failed to give the %s badge to %s!", badge.getName(), member.getEffectiveName())).queue();
                    }
                }
            });
    }

    @SlashCommand(name = "badge", subcommand = "revoke", description = "Remove a badge from a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeRemove(SlashCommandInteractionEvent event, @SlashOption Member member, @SlashOption(autocompleteId = "available_badges") String badgeId, @SlashOption(required = false) int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                Badge badge = BadgeManager.getBadgeById(badgeId);

                if (badge == null) {
                    event.getHook().editOriginal(String.format("%s is an invalid Badge ID!", badgeId)).queue();
                    return;
                }

                if (badge instanceof TieredBadge tieredBadge) {
                    int finalTier = Math.max(tier, 1);
                    if (discordUser.removeBadge(tieredBadge, finalTier)) {
                        event.getHook().editOriginal(String.format("Removed Tier %d of the %s badge from %s!", finalTier, badge.getName(), member.getEffectiveName())).queue();
                        log.info("{} removed tier {} of badge '{}' (ID: {}) from {}", event.getMember().getEffectiveName(), finalTier, badge.getName(), badge.getId(), member.getEffectiveName());
                    } else {
                        event.getHook().editOriginal(String.format("%s does not have Tier %d of the %s badge!", member.getEffectiveName(), finalTier, badge.getName())).queue();
                    }
                } else {
                    if (discordUser.removeBadge(badge)) {
                        event.getHook().editOriginal(String.format("Removed the %s badge from %s!", badge.getName(), member.getEffectiveName())).queue();
                        log.info("{} removed badge '{}' (ID: {}) from {}", event.getMember().getEffectiveName(), badge.getName(), badge.getId(), member.getEffectiveName());
                    } else {
                        event.getHook().editOriginal(String.format("%s does not have the %s badge!", member.getEffectiveName(), badge.getName())).queue();
                    }
                }
            });
    }

    @SlashCommand(name = "badge", subcommand = "list", description = "List all available badges", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void badgeList(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        StringBuilder sb = new StringBuilder("Available Badges (" + BadgeManager.getBadgeMap().size() + "):\n");

        BadgeManager.getBadgeMap().forEach((key, value) -> {
            sb.append("- ");

            if (value.getEmoji() != null) {
                sb.append(value.getFormattedName()).append(" ");
            } else {
                sb.append(value.getName()).append(" ");
            }

            sb.append("(").append(key).append(")\n");

            if (value instanceof TieredBadge tieredBadge) {
                tieredBadge.getTiers().forEach(tier -> sb.append("  - ").append(tier.getFormattedName()).append(" (tier: ").append(tier.getTier()).append(")\n"));
            }
        });

        if (sb.length() > 2048) {
            try {
                event.getHook().editOriginal(MessageEditData.fromFiles(FileUpload.fromData(FileUtils.createTempFile("badges.txt", sb.toString())))).queue();
                return;
            } catch (IOException exception) {
                log.error("Failed to create temp file listing all badges!", exception);
                event.getHook().editOriginal("Failed to list all available badges! Please try again later!").queue();
            }
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @SlashAutocompleteHandler(id = "available_badges")
    public List<Command.Choice> getAvailableBadges(CommandAutoCompleteInteractionEvent event) {
        return BadgeManager.getBadgeMap().entrySet().stream()
            .map(entry -> new Command.Choice(entry.getValue().getName(), entry.getKey()))
            .filter(choice -> choice.getName().contains(event.getFocusedOption().getName()))
            .toList();
    }
}