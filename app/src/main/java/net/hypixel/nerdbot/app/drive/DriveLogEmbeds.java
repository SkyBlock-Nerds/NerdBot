package net.hypixel.nerdbot.app.drive;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Posts Drive access changes to the log channel, mirroring ModLogListener's
 * embed style. Folder names resolve through the service's cached Drive lookup
 * with the raw id shown alongside. Posting is best-effort: a log-channel
 * failure never breaks the permission change it describes.
 */
@Slf4j
public final class DriveLogEmbeds {

    private DriveLogEmbeds() {
    }

    public static void postAccessChange(DrivePermissionService service, String memberId,
                                        List<String> grantedFolders, List<String> revokedFolders) {
        if (grantedFolders.isEmpty() && revokedFolders.isEmpty()) {
            return;
        }

        try {
            Color color = revokedFolders.isEmpty() ? Color.GREEN : grantedFolders.isEmpty() ? Color.RED : Color.ORANGE;
            EmbedBuilder embed = EmbedFactory.create("Drive access updated", null, color)
                .addField("User", "<@" + memberId + ">", false)
                .addField("User ID", memberId, false);

            if (!grantedFolders.isEmpty()) {
                embed.addField("Granted", folderLines(service, grantedFolders), false);
            }
            if (!revokedFolders.isEmpty()) {
                embed.addField("Revoked", folderLines(service, revokedFolders), false);
            }

            ChannelCache.sendToLogChannel(embed.build());
        } catch (Exception e) {
            log.warn("Failed to post Drive access log embed for {}", memberId, e);
        }
    }

    private static String folderLines(DrivePermissionService service, List<String> folderIds) {
        return folderIds.stream()
            .map(folderId -> service.folderDisplayName(folderId) + " (`" + folderId + "`)")
            .collect(Collectors.joining("\n"));
    }
}
