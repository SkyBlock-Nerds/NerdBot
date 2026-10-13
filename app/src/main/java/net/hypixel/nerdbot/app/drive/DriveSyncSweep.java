package net.hypixel.nerdbot.app.drive;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.marmalade.google.drive.DriveApiException;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermission;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Full reconcile pass over every linked member: refresh grants against live
 * guild roles, revoke-and-forget members who left while the bot was offline,
 * and re-create grants that were deleted directly in Drive. Shared by the
 * hourly feature and the /drive sync admin command. Users are processed
 * sequentially with a short pause to stay friendly to Drive API quotas.
 */
@Slf4j
@Singleton
public class DriveSyncSweep {

    private static final long PAUSE_BETWEEN_USERS_MS = 200;

    // Stops the hourly sweep and /drive sync running over the same members at once
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final DiscordUserStore users;
    private final MemberQueue queue;
    private final Provider<Optional<DrivePermissionService>> drive;
    private final Provider<NerdBotConfig> config;
    private final GuildScope guildScope;
    private final JDA jda;
    private final LogChannel logChannel;

    @Inject
    public DriveSyncSweep(DiscordUserStore users, MemberQueue queue, Provider<Optional<DrivePermissionService>> drive, Provider<NerdBotConfig> config,
                          GuildScope guildScope, JDA jda, LogChannel logChannel) {
        this.users = users;
        this.queue = queue;
        this.drive = drive;
        this.config = config;
        this.guildScope = guildScope;
        this.jda = jda;
        this.logChannel = logChannel;
    }

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

    /** Change-notification seam; run() plugs the log-channel embeds in here. */
    public interface ChangeListener {
        void onChange(String memberId, List<String> grantedFolders, List<String> revokedFolders);
    }

    /** Backwards-compatible core without change notifications. */
    public static Result reconcileUsers(Collection<DiscordUser> users, MemberLookup lookup,
                                        DrivePermissionService service, GoogleDriveConfig config, UserSaver saver) {
        return reconcileUsers(users, lookup, service, config, saver, (memberId, granted, revoked) -> { });
    }

    /** JDA-free core, unit tested directly. */
    public static Result reconcileUsers(Collection<DiscordUser> users, MemberLookup lookup,
                                        DrivePermissionService service, GoogleDriveConfig config, UserSaver saver,
                                        ChangeListener changeListener) {
        int synced = 0;
        int departed = 0;
        int failed = 0;

        Map<String, Set<String>> livePermissionIds = fetchLivePermissionIds(service, config);
        DriveLinkWorkflow workflow = new DriveLinkWorkflow(service, config);

        for (DiscordUser user : users) {
            switch (reconcileUser(user, lookup, service, config, workflow, livePermissionIds, saver, changeListener)) {
                case SYNCED -> synced++;
                case DEPARTED -> departed++;
                case FAILED -> failed++;
                case NOT_LINKED -> {
                }
            }
        }

        return new Result(synced, departed, failed);
    }

    enum Change {
        SYNCED,
        DEPARTED,
        FAILED,
        NOT_LINKED
    }

    static Change reconcileUser(DiscordUser user, MemberLookup lookup, DrivePermissionService service, GoogleDriveConfig config,
                                DriveLinkWorkflow workflow, Map<String, Set<String>> livePermissionIds, UserSaver saver,
                                ChangeListener changeListener) {
        if (user.getDriveAccess() == null) {
            return Change.NOT_LINKED;
        }

        Optional<List<String>> roleIds;
        try {
            roleIds = lookup.roleIdsFor(user.getDiscordId());
        } catch (RuntimeException exception) {
            log.warn("Could not resolve member {} this sweep, skipping", user.getDiscordId(), exception);
            return Change.FAILED;
        }

        if (roleIds.isEmpty()) {
            log.info("Member {} no longer in guild; revoking and forgetting their Drive access", user.getDiscordId());
            List<String> heldFolders = user.getDriveAccess().getGrants().stream().map(DriveGrant::folderId).toList();
            workflow.revokeAndForget(user);
            saver.save(user);
            changeListener.onChange(user.getDiscordId(), List.of(), heldFolders);
            return Change.DEPARTED;
        }

        dropDanglingGrants(user, livePermissionIds);
        DrivePermissionService.SyncOutcome outcome = service.syncGrants(user.getDiscordId(), user.getDriveAccess(), roleIds.get(), config);
        saver.save(user);
        if (!outcome.grantedFolders().isEmpty() || !outcome.revokedFolders().isEmpty()) {
            changeListener.onChange(user.getDiscordId(), outcome.grantedFolders(), outcome.revokedFolders());
        }
        return outcome.hasFailures() ? Change.FAILED : Change.SYNCED;
    }

    /**
     * One listPermissions per mapped folder per sweep. A folder we cannot list
     * is left out of the map, which means "no dangling-id check there this
     * round"; grants are never dropped on the basis of a failed list call.
     */
    private static Map<String, Set<String>> fetchLivePermissionIds(DrivePermissionService service, GoogleDriveConfig config) {
        Map<String, Set<String>> live = new HashMap<>();

        for (String folderId : mappedFolderIds(config)) {
            try {
                Set<String> ids = new HashSet<>();
                for (DrivePermission permission : service.listFolderPermissions(folderId)) {
                    ids.add(permission.id());
                }
                live.put(folderId, ids);
            } catch (DriveApiException e) {
                log.warn("Could not list permissions on folder {} this sweep; skipping the dangling-grant check for it", folderId, e);
            }
        }
        return live;
    }

    /** Every folder id referenced anywhere in the config's role mappings, deduped. Shared by discovery and the sweep-start log. */
    private static Set<String> mappedFolderIds(GoogleDriveConfig config) {
        Set<String> mappedFolders = new HashSet<>();
        config.getFolderMappings().forEach(mapping -> mappedFolders.addAll(mapping.getFolderIds()));
        return mappedFolders;
    }

    private static void dropDanglingGrants(DiscordUser user, Map<String, Set<String>> livePermissionIds) {
        user.getDriveAccess().getGrants().removeIf(grant -> {
            Set<String> live = livePermissionIds.get(grant.folderId());
            boolean dangling = live != null && !live.contains(grant.permissionId());
            if (dangling) {
                log.info("Stored Drive grant for {} on folder {} no longer exists in Drive; regranting", user.getDiscordId(), grant.folderId());
            }
            return dangling;
        });
    }

    /**
     * Reconciles every stored member with Drive access. Each member is reconciled in their queue, so
     * the sweep can't race the role listener changing the same grants.
     */
    public Result run() {
        if (!running.compareAndSet(false, true)) {
            log.info("Drive reconcile sweep already in progress, skipping this trigger");
            return new Result(0, 0, 0);
        }

        try {
            Optional<DrivePermissionService> service = drive.get();
            GoogleDriveConfig config = this.config.get().getGoogleDriveConfig();
            if (service.isEmpty() || !config.isEnabled()) {
                return new Result(0, 0, 0);
            }

            Optional<Guild> guild = guildScope.mainGuild(jda);
            if (guild.isEmpty()) {
                log.warn("Not reconciling Drive access; the bot isn't in the configured guild");
                return new Result(0, 0, 0);
            }

            Map<String, Set<String>> livePermissionIds = fetchLivePermissionIds(service.get(), config);
            DriveLinkWorkflow workflow = new DriveLinkWorkflow(service.get(), config);
            MemberLookup lookup = memberId -> roleIdsOf(guild.get(), memberId);
            ChangeListener changes = (memberId, granted, revoked) -> DriveLogEmbeds.postAccessChange(logChannel, service.get(), memberId, granted, revoked);
            log.info("Reconcile sweep starting over {} mapped folder(s)", mappedFolderIds(config).size());

            int synced = 0;
            int departed = 0;
            int failed = 0;
            for (String memberId : users.findAllIds()) {
                AtomicReference<Change> change = new AtomicReference<>(Change.NOT_LINKED);
                try {
                    queue.run(memberId, "Drive sync", () -> users.findById(memberId).ifPresent(user ->
                        change.set(reconcileUser(user, lookup, service.get(), config, workflow, livePermissionIds, this::save, changes)))).join();
                } catch (CompletionException exception) {
                    change.set(Change.FAILED);
                }

                switch (change.get()) {
                    case SYNCED -> synced++;
                    case DEPARTED -> departed++;
                    case FAILED -> failed++;
                    case NOT_LINKED -> {
                    }
                }
            }

            Result result = new Result(synced, departed, failed);
            log.info("Drive reconcile sweep finished: {}", result.summary());
            return result;
        } finally {
            running.set(false);
        }
    }

    // Empty only when Discord says the member is gone; any other failure is thrown and counted as failed
    private static Optional<List<String>> roleIdsOf(Guild guild, String memberId) {
        try {
            return Optional.of(guild.retrieveMemberById(memberId).complete().getRoles().stream().map(Role::getId).toList());
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER || exception.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    // Paced, so a large sweep stays within the Drive API's quotas
    private void save(DiscordUser user) {
        if (user.getDriveAccess() == null) {
            users.clearDriveAccess(user);
        } else {
            users.save(user).join();
        }

        try {
            Thread.sleep(PAUSE_BETWEEN_USERS_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
