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

    @ExampleValue("false")
    private boolean enabled = false;

    private List<DriveFolderMapping> folderMappings = new ArrayList<>();

    /**
     * Whether Google sends its share-notification email when the bot grants
     * folder access. Revokes never notify; that is a Drive limitation.
     */
    @ExampleValue("true")
    private boolean sendNotificationEmails = true;

    /**
     * Custom text Google includes in the share-notification email. Helps the
     * email explain itself, since service accounts cannot have a friendly
     * sender name. Blank uses Google's default wording; ignored entirely when
     * sendNotificationEmails is off.
     */
    @ExampleValue("This folder was shared automatically by the SkyBlock Nerds bot based on your Discord roles.")
    private String notificationEmailMessage = "This folder was shared automatically by the SkyBlock Nerds bot based on your Discord roles.";
}
