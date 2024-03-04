package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.badge.BadgeManager;
import net.hypixel.nerdbot.api.badge.TieredBadge;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Log4j2
public class BadgeCommands extends ApplicationCommand {

    @JDASlashCommand(name = "badge", subcommand = "give", description = "Award a badge to a user", defaultLocked = true)
    public void badgeAward(GuildSlashEvent event, @AppOption Member member, @AppOption(autocomplete = "available_badges") String badgeId, @AppOption @Optional int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

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
            tier = tier < 1 ? 1 : tier;

            if (tier > tieredBadge.getTiers().size()) {
                TranslationManager.edit(event.getHook(), "commands.badge.invalid_tier", badge.getName(), tieredBadge.getTiers().size());
                return;
            }

            if (discordUser.addBadge(tieredBadge, tier)) {
                TranslationManager.edit(event.getHook(), "commands.badge.gave_tier", tier, badge.getName(), member.getEffectiveName());
                log.info(event.getMember().getEffectiveName() + " gave " + member.getEffectiveName() + " tier " + tier + " of badge '" + badge.getName() + "' (ID: " + badge.getId() + ")");
            } else {
                TranslationManager.edit(event.getHook(), "commands.badge.already_has_tier", member.getEffectiveName(), tier, badge.getName());
            }
        } else {
            if (discordUser.hasBadge(badge)) {
                TranslationManager.edit(event.getHook(), "commands.badge.already_has_badge", member.getEffectiveName(), badge.getName());
                return;
            }

            if (discordUser.addBadge(badge)) {
                TranslationManager.edit(event.getHook(), "commands.badge.gave_badge", badge.getName(), member.getEffectiveName());
                log.info(event.getMember().getEffectiveName() + " gave " + member.getEffectiveName() + " badge '" + badge.getName() + "' (ID: " + badge.getId() + ")");
            } else {
                TranslationManager.edit(event.getHook(), "commands.badge.failed_to_give_badge", badge.getName(), member.getEffectiveName());
            }
        }
    }

    @JDASlashCommand(name = "badge", subcommand = "revoke", description = "Remove a badge from a user", defaultLocked = true)
    public void badgeRemove(GuildSlashEvent event, @AppOption Member member, @AppOption(autocomplete = "available_badges") String badgeId, @AppOption @Optional int tier) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(member.getId());

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
            tier = tier < 1 ? 1 : tier;
            if (discordUser.removeBadge(tieredBadge, tier)) {
                TranslationManager.edit(event.getHook(), "commands.badge.removed_tier", tier, badge.getName(), member.getEffectiveName());
                log.info(event.getMember().getEffectiveName() + " removed tier " + tier + " of badge '" + badge.getName() + "' (ID: " + badge.getId() + ") from " + member.getEffectiveName());
            } else {
                TranslationManager.edit(event.getHook(), "commands.badge.does_not_have_tier", member.getEffectiveName(), tier, badge.getName());
            }
        } else {
            if (discordUser.removeBadge(badge)) {
                TranslationManager.edit(event.getHook(), "commands.badge.removed_badge", badge.getName(), member.getEffectiveName());
                log.info(event.getMember().getEffectiveName() + " removed badge '" + badge.getName() + "' (ID: " + badge.getId() + ") from " + member.getEffectiveName());
            } else {
                TranslationManager.edit(event.getHook(), "commands.badge.does_not_have_badge", member.getEffectiveName(), badge.getName());
            }
        }
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
                tieredBadge.getTiers().forEach(tier -> sb.append("  - ").append(tier.getFormattedName()).append(" (").append(tier.getTier()).append(")\n"));
            }
        });

        if (sb.length() > 2048) {
            try {
                event.getHook().editOriginal(MessageEditData.fromFiles(FileUpload.fromData(Util.createTempFile("badges.txt", sb.toString())))).queue();
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
