package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.badge.BadgeManager;
import net.hypixel.nerdbot.app.role.RoleIdSync;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.RepositoryDiscordUserStore;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.util.DiscordUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Slf4j
public class UserGrabberFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.warn("Bot is in read-only mode, skipping user grabber task!");
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            log.error("Can't initiate feature as the database is not connected!");
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUserStore store = new RepositoryDiscordUserStore(discordUserRepository);
        Guild guild = DiscordUtils.getMainGuild();
        log.info("Grabbing users from guild {} (ID: {})", guild.getName(), guild.getId());

        guild.loadMembers(member -> {
                if (member.getUser().isBot()) {
                    return;
                }

                log.info("Found user {} ({})", member.getEffectiveName(), member.getId());
                List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();
                reconcileMember(store, member.getId(), member.getEffectiveName(), UserGrabberFeature::isKnownBadge, roleIds);
            })
            .onSuccess(aVoid -> log.info("Finished grabbing users from guild {} (ID: {})", guild.getName(), guild.getId()))
            .onError(throwable -> log.error("Failed to grab users from guild {} (ID: {})", guild.getName(), guild.getId(), throwable));
    }

    /**
     * Reconcile a single guild member into the store: look the user up (creating a fresh one if
     * absent), backfill any null activity/badge fields, drop badges the bot no longer recognises,
     * and save.
     *
     * <p>Extracted from {@link #onFeatureStart()} so the reconciliation can be exercised against an
     * in-memory {@link DiscordUserStore} without a live bot, database or JDA guild. Badge validity
     * is supplied as a predicate rather than read from the static {@link BadgeManager}, so tests
     * control which badges are known.
     *
     * @param store         the user store to reconcile into
     * @param memberId      the member's Discord ID
     * @param effectiveName the member's display name (used only for logging)
     * @param knownBadge    returns {@code true} if the given badge ID is still recognised
     * @param roleIds       the member's current Discord role ids
     * @return the reconciled (and saved) user
     */
    static DiscordUser reconcileMember(DiscordUserStore store, String memberId, String effectiveName, Predicate<String> knownBadge, List<String> roleIds) {
        DiscordUser discordUser = store.findById(memberId).orElse(null);
        if (discordUser == null) {
            discordUser = new DiscordUser(memberId);
            log.info("Creating new DiscordUser for user {}", memberId);
        }

        if (discordUser.getLastActivity() == null) {
            log.info("Last activity for {} (ID: {}) was null. Setting to default values!", effectiveName, memberId);
            discordUser.setLastActivity(new LastActivity());
        }

        if (discordUser.getBadges() == null) {
            log.info("Badges for {} (ID: {}) was null. Setting to default values!", effectiveName, memberId);
            discordUser.setBadges(new ArrayList<>());
        }

        discordUser.getBadges().removeIf(badgeEntry -> {
            boolean unknown = !knownBadge.test(badgeEntry.badgeId());
            if (unknown) {
                log.warn("Badge '{}' for {} (ID: {}) was not found in the badge map! Removing...", badgeEntry, effectiveName, memberId);
            }
            return unknown;
        });

        if (RoleIdSync.applyRoleIds(discordUser, roleIds)) {
            log.info("Updated role ids for {} (ID: {})", effectiveName, memberId);
        }

        store.save(discordUser);
        return discordUser;
    }

    private static boolean isKnownBadge(String badgeId) {
        return BadgeManager.getBadgeById(badgeId) != null || BadgeManager.getTieredBadgeById(badgeId) != null;
    }

    @Override
    public void onFeatureEnd() {
    }
}
