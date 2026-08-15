package net.hypixel.nerdbot.app.drive;

import net.hypixel.nerdbot.marmalade.google.drive.DriveAccessLevel;
import net.hypixel.nerdbot.marmalade.google.drive.DriveApiException;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermission;
import net.hypixel.nerdbot.marmalade.google.drive.DrivePermissionClient;
import net.hypixel.nerdbot.marmalade.google.drive.TransientDriveApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory Drive: tracks permissions per folder, hands out sequential ids, and
 * can be programmed to fail specific folders permanently or transiently-N-times.
 */
public class FakeDrivePermissionClient implements DrivePermissionClient {

    /** Optional folder-name overrides for getFileName; unset ids echo a stub name. */
    public final Map<String, String> fileNames = new HashMap<>();

    @Override
    public String getFileName(String fileId) {
        return fileNames.getOrDefault(fileId, "Folder " + fileId);
    }

    public final Map<String, List<DrivePermission>> folders = new HashMap<>();
    public final List<String> operations = new ArrayList<>();
    private final Map<String, Integer> transientFailuresRemaining = new HashMap<>();
    private final Map<String, Integer> permanentFailureStatus = new HashMap<>();
    private int nextId = 1;

    public void failTransiently(String folderId, int times) {
        transientFailuresRemaining.put(folderId, times);
    }

    public void failPermanently(String folderId, int status) {
        permanentFailureStatus.put(folderId, status);
    }

    private void maybeFail(String folderId) throws DriveApiException {
        Integer remaining = transientFailuresRemaining.get(folderId);
        if (remaining != null && remaining > 0) {
            transientFailuresRemaining.put(folderId, remaining - 1);
            throw new TransientDriveApiException(503, "scripted transient failure for {}", folderId);
        }
        Integer status = permanentFailureStatus.get(folderId);
        if (status != null) {
            throw new DriveApiException(status, "scripted permanent failure for {}", folderId);
        }
    }

    @Override
    public String grantPermission(String folderId, String email, DriveAccessLevel level) throws DriveApiException {
        maybeFail(folderId);
        String id = "perm-" + nextId++;
        folders.computeIfAbsent(folderId, k -> new ArrayList<>()).add(new DrivePermission(id, email, level.getApiRole()));
        operations.add("grant:" + folderId + ":" + email + ":" + level);
        return id;
    }

    @Override
    public void revokePermission(String folderId, String permissionId) throws DriveApiException {
        maybeFail(folderId);
        folders.getOrDefault(folderId, new ArrayList<>()).removeIf(p -> p.id().equals(permissionId));
        operations.add("revoke:" + folderId + ":" + permissionId);
    }

    @Override
    public List<DrivePermission> listPermissions(String folderId) throws DriveApiException {
        maybeFail(folderId);
        operations.add("list:" + folderId);
        return List.copyOf(folders.getOrDefault(folderId, new ArrayList<>()));
    }
}
