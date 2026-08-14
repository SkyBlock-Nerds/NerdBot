package net.hypixel.nerdbot.app.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Drive folder-permission sync. Disabled by default; even when enabled,
 * the subsystem stays inert unless the JVM was started with the
 * drive.credentials.path and drive.email.key system properties (secrets never
 * live in this file).
 */
@Getter
@Setter
@ToString
public class GoogleDriveConfig {

    private boolean enabled = false;
    private List<DriveFolderMapping> folderMappings = new ArrayList<>();
}
