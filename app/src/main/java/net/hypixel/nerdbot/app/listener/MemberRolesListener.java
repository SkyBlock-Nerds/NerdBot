package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DriveLogEmbeds;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.role.RoleIdSync;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps a member's stored role ids, which the Minecraft network reads, and their shared-Drive folder
 * access in step with their Discord roles. The hourly role reconcile and Drive sweep catch changes
 * made while the bot was offline.
 */
@Slf4j
public class MemberRolesListener {

    private final GuildScope guildScope;
    private final MemberQueue queue;
    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final Provider<Optional<DrivePermissionService>> drive;
    private final LogChannel logChannel;

    @Inject
    public MemberRolesListener(GuildScope guildScope, MemberQueue queue, DiscordUserStore users, Provider<NerdBotConfig> config,
                               Provider<Optional<DrivePermissionService>> drive, LogChannel logChannel) {
        this.guildScope = guildScope;
        this.queue = queue;
        this.users = users;
        this.config = config;
        this.drive = drive;
        this.logChannel = logChannel;
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && !event.getUser().isBot()) {
            sync(event.getMember());
        }
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && !event.getUser().isBot()) {
            sync(event.getMember());
        }
    }

    CompletableFuture<Void> sync(Member member) {
        return queue.withRecord(member.getId(), "role update", user -> {
            // Read when the change runs rather than when it was queued, so the latest roles win
            List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();
            boolean changed = RoleIdSync.applyRoleIds(user, roleIds);
            changed |= syncDrive(user, roleIds);

            if (changed) {
                users.save(user).join();
                log.info("Updated the roles of member {} ({} roles)", member.getId(), roleIds.size());
            }
        });
    }

    private boolean syncDrive(DiscordUser user, List<String> roleIds) {
        Optional<DrivePermissionService> service = drive.get();
        if (user.getDriveAccess() == null || service.isEmpty() || !config.get().getGoogleDriveConfig().isEnabled()) {
            return false;
        }

        DrivePermissionService.SyncOutcome outcome = service.get().syncGrants(user.getDiscordId(), user.getDriveAccess(), roleIds,
            config.get().getGoogleDriveConfig());
        if (outcome.grantedFolders().isEmpty() && outcome.revokedFolders().isEmpty() && !outcome.hasFailures()) {
            return false;
        }

        log.info("Drive resync for {}: +{} -{} !{}", user.getDiscordId(),
            outcome.grantedFolders().size(), outcome.revokedFolders().size(), outcome.failedFolders().size());
        DriveLogEmbeds.postAccessChange(logChannel, service.get(), user.getDiscordId(), outcome.grantedFolders(), outcome.revokedFolders());
        return true;
    }
}
