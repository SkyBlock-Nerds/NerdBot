package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.badge.BadgeManager;
import net.hypixel.nerdbot.api.badge.TieredBadge;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.FileUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class BadgeCommands extends ApplicationCommand {

    @JDASlashCommand(name = "badge", subcommand = "give", description = "Award a badge to a user", defaultLocked = true)
    public void badgeAward(GuildSlashEvent event, @AppOption Member member, @AppOption(autocomplete = "available_badges") String badgeId, @AppOption @Optional int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        
        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

                Badge badge = BadgeManager.getBadgeById(badgeId);

                if (badge == null) {
                    TranslationManager.edit(event.getHook(), "commands.badge.invalid_id", badgeId);
                    return;
                }

                if (badge instanceof TieredBadge tieredBadge) {
                    int finalTier = tier < 1 ? 1 : tier;

                    if (finalTier > tieredBadge.getTiers().size()) {
                        TranslationManager.edit(event.getHook(), "commands.badge.invalid_tier", badge.getName(), tieredBadge.getTiers().size());
                        return;
                    }

                    if (discordUser.addBadge(tieredBadge, finalTier)) {
                        TranslationManager.edit(event.getHook(), "commands.badge.gave_tier", finalTier, badge.getName(), member.getEffectiveName());
                        log.info("{} gave {} tier {} of badge '{}' (ID: {})", event.getMember().getEffectiveName(), member.getEffectiveName(), finalTier, badge.getName(), badge.getId());
                    } else {
                        TranslationManager.edit(event.getHook(), "commands.badge.already_has_tier", member.getEffectiveName(), finalTier, badge.getName());
                    }
                } else {
                    if (discordUser.hasBadge(badge)) {
                        TranslationManager.edit(event.getHook(), "commands.badge.already_has_badge", member.getEffectiveName(), badge.getName());
                        return;
                    }

                    if (discordUser.addBadge(badge)) {
                        TranslationManager.edit(event.getHook(), "commands.badge.gave_badge", badge.getName(), member.getEffectiveName());
                        log.info("{} gave {} badge '{}' (ID: {})", event.getMember().getEffectiveName(), member.getEffectiveName(), badge.getName(), badge.getId());
                    } else {
                        TranslationManager.edit(event.getHook(), "commands.badge.failed_to_give_badge", badge.getName(), member.getEffectiveName());
                    }
                }
            });
    }

    @JDASlashCommand(name = "badge", subcommand = "revoke", description = "Remove a badge from a user", defaultLocked = true)
    public void badgeRemove(GuildSlashEvent event, @AppOption Member member, @AppOption(autocomplete = "available_badges") String badgeId, @AppOption @Optional int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        
        discordUserRepository.findByIdAsync(member.getId())
            .thenAccept(discordUser -> {
                if (discordUser == null) {
                    TranslationManager.edit(event.getHook(), "generic.user_not_found");
                    return;
                }

                Badge badge = BadgeManager.getBadgeById(badgeId);

                if (badge == null) {
                    TranslationManager.edit(event.getHook(), "commands.badge.invalid_badge", badgeId);
                    return;
                }

                if (badge instanceof TieredBadge tieredBadge) {
                    int finalTier = Math.max(tier, 1);
                    if (discordUser.removeBadge(tieredBadge, finalTier)) {
                        TranslationManager.edit(event.getHook(), "commands.badge.removed_tier", finalTier, badge.getName(), member.getEffectiveName());
                        log.info("{} removed tier {} of badge '{}' (ID: {}) from {}", event.getMember().getEffectiveName(), finalTier, badge.getName(), badge.getId(), member.getEffectiveName());
                    } else {
                        TranslationManager.edit(event.getHook(), "commands.badge.does_not_have_tier", member.getEffectiveName(), finalTier, badge.getName());
                    }
                } else {
                    if (discordUser.removeBadge(badge)) {
                        TranslationManager.edit(event.getHook(), "commands.badge.removed_badge", badge.getName(), member.getEffectiveName());
                        log.info("{} removed badge '{}' (ID: {}) from {}", event.getMember().getEffectiveName(), badge.getName(), badge.getId(), member.getEffectiveName());
                    } else {
                        TranslationManager.edit(event.getHook(), "commands.badge.does_not_have_badge", member.getEffectiveName(), badge.getName());
                    }
                }
            });
    }

    @JDASlashCommand(name = "badge", subcommand = "list", description = "List all available badges", defaultLocked = true)
    public void badgeList(GuildSlashEvent event) {
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
                TranslationManager.edit(event.getHook(), "commands.badge.failed_to_list");
            }
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @AutocompletionHandler(name = "available_badges", showUserInput = false)
    public List<String> getAvailableBadges(CommandAutoCompleteInteractionEvent event) {
        return Arrays.asList(BadgeManager.getBadgeMap().keySet().toArray(new String[0]));
    }
}
