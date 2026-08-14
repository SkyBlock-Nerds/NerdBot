package net.hypixel.nerdbot.app.drive;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.marmalade.google.drive.DriveApiException;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermission;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Full reconcile pass over every linked member: refresh grants against live
 * guild roles, revoke-and-forget members who left while the bot was offline,
 * and re-create grants that were deleted directly in Drive. Shared by the
 * hourly feature and the /drive sync admin command. Users are processed
 * sequentially with a short pause to stay friendly to Drive API quotas.
 */
@Slf4j
public class DriveSyncSweep {

    private static final long PAUSE_BETWEEN_USERS_MS = 200;

    public record Result(int synced, int departed, int failed) {

        public String summary() {
            return synced + " member(s) synced, " + departed + " departed member(s) cleaned up, " + failed + " with failures";
        }
    }

    /** Looks up a member's current role ids; empty means they left the guild. */
    public interface MemberLookup {
        Optional<List<String>> roleIdsFor(String discordId);
    }

    /** Persistence seam so the core logic tests without a database. */
    public interface UserSaver {
        void save(DiscordUser user);
    }

    /** JDA-free core, unit tested directly. */
    public static Result reconcileUsers(Collection<DiscordUser> users, MemberLookup lookup,
                                        DrivePermissionService service, GoogleDriveConfig config, UserSaver saver) {
        int synced = 0;
        int departed = 0;
        int failed = 0;

        Map<String, Set<String>> livePermissionIds = fetchLivePermissionIds(service, config);
        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service, config);

        for (DiscordUser user : users) {
            if (user.getDriveAccess() == null) {
                continue;
            }

            Optional<List<String>> roleIds;
            try {
                roleIds = lookup.roleIdsFor(user.getDiscordId());
            } catch (Exception e) {
                log.warn("Could not resolve member {} this sweep — skipping", user.getDiscordId(), e);
                failed++;
                continue;
            }

            if (roleIds.isEmpty()) {
                workflow.revokeAndForget(user);
                saver.save(user);
                departed++;
                continue;
            }

            dropDanglingGrants(user, livePermissionIds);
            DrivePermissionService.SyncOutcome outcome = service.syncGrants(user.getDriveAccess(), roleIds.get(), config);
            saver.save(user);
            if (outcome.hasFailures()) {
                failed++;
            } else {
                synced++;
            }
        }

        return new Result(synced, departed, failed);
    }

    /**
     * One listPermissions per mapped folder per sweep. A folder we cannot list
     * is left out of the map, which means "no dangling-id check there this
     * round" — grants are never dropped on the basis of a failed list call.
     */
    private static Map<String, Set<String>> fetchLivePermissionIds(DrivePermissionService service, GoogleDriveConfig config) {
        Map<String, Set<String>> live = new HashMap<>();
        Set<String> mappedFolders = new HashSet<>();
        config.getFolderMappings().forEach(mapping -> mappedFolders.addAll(mapping.getFolderIds()));

        for (String folderId : mappedFolders) {
            try {
                Set<String> ids = new HashSet<>();
                for (DrivePermission permission : service.listFolderPermissions(folderId)) {
                    ids.add(permission.id());
                }
                live.put(folderId, ids);
            } catch (DriveApiException e) {
                log.warn("Could not list permissions on folder {} this sweep — skipping dangling-grant check for it", folderId, e);
            }
        }
        return live;
    }

    private static void dropDanglingGrants(DiscordUser user, Map<String, Set<String>> livePermissionIds) {
        user.getDriveAccess().getGrants().removeIf(grant -> {
            Set<String> live = livePermissionIds.get(grant.folderId());
            boolean dangling = live != null && !live.contains(grant.permissionId());
            if (dangling) {
                log.info("Stored Drive grant for {} on folder {} no longer exists in Drive — will regrant", user.getDiscordId(), grant.folderId());
            }
            return dangling;
        });
    }

    /** Production entry point: adapts the live guild + repository. */
    public Result run() {
        Optional<DrivePermissionService> service = SkyBlockNerdsBot.drivePermissionService();
        if (service.isEmpty()) {
            return new Result(0, 0, 0);
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        Guild guild = DiscordUtils.getMainGuild();
        GoogleDriveConfig config = SkyBlockNerdsBot.config().getGoogleDriveConfig();

        MemberLookup lookup = discordId -> {
            try {
                return Optional.of(guild.retrieveMemberById(discordId).complete()
                    .getRoles().stream().map(Role::getId).toList());
            } catch (ErrorResponseException e) {
                // Only return empty if we can confirm the member is gone
                if (e.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER ||
                    e.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                    return Optional.empty(); // member left the guild
                }
                // For any other error, rethrow so it's caught by reconcileUsers and counted as failed
                throw e;
            }
        };

        List<DiscordUser> linkedUsers = repository.getAllDocuments().stream()
            .filter(user -> user.getDriveAccess() != null)
            .toList();

        // Wrap the saver with pacing so a large sweep doesn't hammer the API
        UserSaver saver = user -> {
            repository.cacheObject(user);
            repository.saveToDatabaseAsync(user);
            try {
                Thread.sleep(PAUSE_BETWEEN_USERS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Result result = reconcileUsers(linkedUsers, lookup, service.get(), config, saver);
        log.info("Drive reconcile sweep finished: {}", result.summary());
        return result;
    }
}
