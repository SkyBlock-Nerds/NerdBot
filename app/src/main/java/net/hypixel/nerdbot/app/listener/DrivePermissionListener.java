package net.hypixel.nerdbot.app.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;

/**
 * Mirrors Discord role changes onto shared-Drive folder permissions as they
 * happen, and revokes + forgets everything when a member leaves (which covers
 * kicks and bans too). The hourly DrivePermissionSyncFeature is the safety net
 * for anything this misses while the bot is offline.
 */
@Slf4j
public class DrivePermissionListener {

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        resync(event.getMember());
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        resync(event.getMember());
    }

    @SubscribeEvent
    public void onMemberRemove(GuildMemberRemoveEvent event) {
        SkyBlockNerdsBot.drivePermissionService().ifPresent(service -> {
            DiscordUserRepository repository = repository();
            repository.findByIdAsync(event.getUser().getId()).thenAccept(user -> {
                if (user == null || user.getDriveAccess() == null) {
                    return;
                }
                new DriveLinkWorkflow(service, SkyBlockNerdsBot.config().getGoogleDriveConfig()).revokeAndForget(user);
                repository.cacheObject(user);
                repository.saveToDatabaseAsync(user);
                log.info("Revoked Drive access for departing member {}", event.getUser().getId());
            });
        });
    }

    private void resync(Member member) {
        if (member.getUser().isBot()) {
            return;
        }

        SkyBlockNerdsBot.drivePermissionService().ifPresent(service -> {
            DiscordUserRepository repository = repository();
            repository.findByIdAsync(member.getId()).thenAccept(user -> {
                if (user == null || user.getDriveAccess() == null) {
                    return;
                }
                List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();
                DrivePermissionService.SyncOutcome outcome = service.syncGrants(
                    member.getId(), user.getDriveAccess(), roleIds, SkyBlockNerdsBot.config().getGoogleDriveConfig());
                repository.cacheObject(user);
                repository.saveToDatabaseAsync(user);
                if (!outcome.grantedFolders().isEmpty() || !outcome.revokedFolders().isEmpty() || outcome.hasFailures()) {
                    log.info("Drive resync for {}: +{} -{} !{}", member.getId(),
                        outcome.grantedFolders().size(), outcome.revokedFolders().size(), outcome.failedFolders().size());
                } else {
                    log.debug("Drive resync for {}: no changes", member.getId());
                }
            });
        });
    }

    private static DiscordUserRepository repository() {
        return BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
    }
}
