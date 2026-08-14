package net.hypixel.nerdbot.app.drive;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.marmalade.google.GoogleAuthException;
import net.hypixel.nerdbot.marmalade.google.GoogleTokenProvider;
import net.hypixel.nerdbot.marmalade.google.ServiceAccountKey;
import net.hypixel.nerdbot.marmalade.google.drive.DriveAccessLevel;
import net.hypixel.nerdbot.marmalade.google.drive.DriveApiException;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermission;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermissionClient;
import net.hypixel.nerdbot.marmalade.google.drive.HttpDrivePermissionClient;
import net.hypixel.nerdbot.marmalade.google.drive.TransientDriveApiException;
import net.hypixel.nerdbot.marmalade.resilience.Retry;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.security.CipherException;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveAccess;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Grants and revokes shared-Drive folder permissions so they mirror a member's
 * Discord roles. All Drive calls retry transient failures; permanent failures
 * are reported per-folder and the stored grant state stays consistent either
 * way, so the hourly reconcile sweep can heal anything that slipped through.
 * Emails only ever exist in plaintext transiently in memory — never in logs,
 * never in the database.
 */
@Slf4j
public class DrivePermissionService {

    public static final String CREDENTIALS_PATH_PROPERTY = "drive.credentials.path";
    public static final String EMAIL_KEY_PROPERTY = "drive.email.key";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_ATTEMPTS = 3;

    private final AesGcmCipher cipher;
    private final DrivePermissionClient client;

    public DrivePermissionService(AesGcmCipher cipher, DrivePermissionClient client) {
        this.cipher = cipher;
        this.client = client;
    }

    /**
     * Builds the service from JVM system properties, or empty when the feature
     * is disabled or incompletely configured — callers treat empty as "feature
     * off" and stay inert.
     */
    public static Optional<DrivePermissionService> fromSystemProperties(GoogleDriveConfig config) {
        if (config == null || !config.isEnabled()) {
            log.info("Google Drive permission sync is disabled in config");
            return Optional.empty();
        }

        String credentialsPath = System.getProperty(CREDENTIALS_PATH_PROPERTY);
        String emailKey = System.getProperty(EMAIL_KEY_PROPERTY);

        if (credentialsPath == null || emailKey == null) {
            log.warn("Google Drive sync enabled in config but missing {} or {} system property — feature stays off",
                CREDENTIALS_PATH_PROPERTY, EMAIL_KEY_PROPERTY);
            return Optional.empty();
        }

        try {
            AesGcmCipher cipher = AesGcmCipher.fromBase64Key(emailKey);
            ServiceAccountKey key = ServiceAccountKey.fromFile(Path.of(credentialsPath));
            DrivePermissionClient client = HttpDrivePermissionClient.createDefault(new GoogleTokenProvider(key));
            log.info("Google Drive permission sync active ({} folder mappings)", config.getFolderMappings().size());
            return Optional.of(new DrivePermissionService(cipher, client));
        } catch (GoogleAuthException | IllegalArgumentException e) {
            log.error("Google Drive sync misconfigured — feature stays off", e);
            return Optional.empty();
        }
    }

    /**
     * The folder access a member with these roles should have: union across all
     * mapped roles they hold, most permissive level per folder. Pure function.
     */
    public static Map<String, DriveAccessLevel> computeDesiredGrants(Collection<String> roleIds, GoogleDriveConfig config) {
        Map<String, DriveAccessLevel> desired = new HashMap<>();
        if (config == null || !config.isEnabled() || roleIds == null) {
            return desired;
        }

        Set<String> held = new HashSet<>(roleIds);
        for (DriveFolderMapping mapping : config.getFolderMappings()) {
            if (!held.contains(mapping.getRoleId())) {
                continue;
            }

            Optional<DriveAccessLevel> level = parseLevel(mapping.getAccessLevel());
            if (level.isEmpty()) {
                log.warn("Ignoring Drive mapping for role {}: unknown access level '{}'", mapping.getRoleId(), mapping.getAccessLevel());
                continue;
            }

            for (String folderId : mapping.getFolderIds()) {
                desired.merge(folderId, level.get(), (a, b) -> b.outranks(a) ? b : a);
            }
        }
        return desired;
    }

    private static Optional<DriveAccessLevel> parseLevel(String name) {
        try {
            return Optional.of(DriveAccessLevel.valueOf(name));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    /** What one sync pass changed in Drive, for command feedback and logs. */
    public record SyncOutcome(List<String> grantedFolders, List<String> revokedFolders, List<String> failedFolders) {

        public boolean hasFailures() {
            return !failedFolders.isEmpty();
        }
    }

    /**
     * Converges this member's Drive permissions onto what their roles say they
     * should have. Mutates {@code access}; the caller persists the user.
     */
    public SyncOutcome syncGrants(DriveAccess access, Collection<String> roleIds, GoogleDriveConfig config) {
        Map<String, DriveAccessLevel> desired = computeDesiredGrants(roleIds, config);
        List<String> granted = new ArrayList<>();
        List<String> revoked = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        Optional<String> email = decryptEmail(access);
        if (email.isEmpty()) {
            // Can't grant without the address; can't safely revoke either (state may be fine)
            return new SyncOutcome(granted, revoked, new ArrayList<>(desired.keySet()));
        }

        // Revoke grants that are stale or at the wrong level
        List<DriveGrant> keptGrants = new ArrayList<>();
        for (DriveGrant grant : access.getGrants()) {
            DriveAccessLevel wanted = desired.get(grant.folderId());
            boolean levelMatches = wanted != null && wanted.name().equals(grant.accessLevel());
            if (levelMatches) {
                keptGrants.add(grant);
                continue;
            }

            try {
                withRetry(() -> {
                    client.revokePermission(grant.folderId(), grant.permissionId());
                    return null;
                });
                revoked.add(grant.folderId());
            } catch (DriveApiException e) {
                log.error("Failed to revoke Drive permission on folder {} (kept for reconcile)", grant.folderId(), e);
                failed.add(grant.folderId());
                keptGrants.add(grant); // still tracked so reconcile retries the revoke
            }
        }

        // Grant what's missing
        Set<String> alreadyGranted = new HashSet<>();
        for (DriveGrant grant : keptGrants) {
            alreadyGranted.add(grant.folderId());
        }
        for (Map.Entry<String, DriveAccessLevel> entry : desired.entrySet()) {
            if (alreadyGranted.contains(entry.getKey()) || failed.contains(entry.getKey())) {
                continue;
            }

            try {
                String permissionId = withRetry(() -> client.grantPermission(entry.getKey(), email.get(), entry.getValue()));
                keptGrants.add(new DriveGrant(entry.getKey(), permissionId, entry.getValue().name()));
                granted.add(entry.getKey());
            } catch (DriveApiException e) {
                log.error("Failed to grant Drive permission on folder {} (reconcile will retry)", entry.getKey(), e);
                failed.add(entry.getKey());
            }
        }

        access.setGrants(keptGrants);
        access.setLastSyncedAt(System.currentTimeMillis());
        return new SyncOutcome(granted, revoked, failed);
    }

    /**
     * Revokes every tracked grant, retrying failures inline — this backs the
     * leave/ban/unlink flows where the state is about to be deleted, so the
     * reconcile sweep cannot heal a miss afterwards.
     *
     * @return false if any revoke ultimately failed (caller should log loudly)
     */
    public boolean revokeAll(DriveAccess access) {
        boolean allRevoked = true;
        List<DriveGrant> remaining = new ArrayList<>();

        for (DriveGrant grant : access.getGrants()) {
            try {
                withRetry(() -> {
                    client.revokePermission(grant.folderId(), grant.permissionId());
                    return null;
                });
            } catch (DriveApiException e) {
                log.error("Failed to revoke Drive permission on folder {} during full revoke", grant.folderId(), e);
                remaining.add(grant);
                allRevoked = false;
            }
        }

        access.setGrants(remaining);
        return allRevoked;
    }

    private <T> T withRetry(net.hypixel.nerdbot.marmalade.functional.ThrowingSupplier<T, ? extends Exception> operation) throws DriveApiException {
        try {
            return Retry.<T>of(operation)
                .maxAttempts(MAX_ATTEMPTS)
                .delay(Duration.ofSeconds(2))
                .backoffMultiplier(2.0)
                .jitterFactor(0.2)
                .retryOn(TransientDriveApiException.class)
                .execute();
        } catch (Retry.RetryExhaustedException e) {
            if (e.getFailures().getLast() instanceof DriveApiException driveFailure) {
                throw driveFailure;
            }
            throw new DriveApiException(-1, "Drive operation failed", e);
        }
    }

    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    public static boolean isValidEmail(String normalized) {
        return normalized != null && normalized.length() <= 254 && EMAIL_PATTERN.matcher(normalized).matches();
    }

    public String encryptEmail(String email) {
        return cipher.encrypt(email);
    }

    public String hashEmail(String email) {
        return cipher.hmac(email);
    }

    public Optional<String> decryptEmail(DriveAccess access) {
        try {
            return Optional.of(cipher.decrypt(access.getEncryptedEmail()));
        } catch (CipherException e) {
            log.warn("Stored Drive email could not be decrypted (key rotation or corrupt data) — treating member as unsyncable", e);
            return Optional.empty();
        }
    }

    /**
     * Lists all current permissions on a folder. Used by the reconcile sweep to
     * detect permissions that were deleted directly in Drive so they can be
     * re-granted if the user still holds the mapped role.
     */
    public List<DrivePermission> listFolderPermissions(String folderId) throws DriveApiException {
        return withRetry(() -> client.listPermissions(folderId));
    }
}
