package net.hypixel.nerdbot.app.feature;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.role.RoleIdSync;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Hourly sweep that re-syncs every guild member's role ids, catching changes
 * made while the bot was offline. Event-driven updates (RoleSyncListener) are
 * the primary path; this is the safety net.
 */
@Slf4j
public class RoleReconcileFeature extends BotFeature {

    private static final long PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    public void onFeatureStart() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.warn("Bot is in read-only mode, skipping role reconcile task!");
            return;
        }
        scheduleAtFixedRate("role-reconcile-task", this::reconcileAll, PERIOD_MS, PERIOD_MS);
    }

    private void reconcileAll() {
        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            log.error("Skipping role reconcile as the database is not connected!");
            return;
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        Guild guild = DiscordUtils.getMainGuild();

        guild.loadMembers(member -> {
                if (member.getUser().isBot()) {
                    return;
                }
                DiscordUser user = repository.findById(member.getId()).toOptional().orElse(null);
                if (user == null) {
                    return;
                }
                List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();
                if (RoleIdSync.applyRoleIds(user, roleIds)) {
                    repository.cacheObject(user);
                    repository.saveToDatabaseAsync(user);
                    log.info("Reconciled role ids for {} ({} roles)", member.getId(), roleIds.size());
                }
            })
            .onError(throwable -> log.error("Role reconcile sweep failed", throwable));
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
