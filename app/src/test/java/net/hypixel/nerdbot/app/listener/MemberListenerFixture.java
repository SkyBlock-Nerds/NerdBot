package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.GoogleDriveConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.drive.FakeDrivePermissionClient;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;

/**
 * What the member listeners are built from: a store, a queue that runs work on the calling thread,
 * and a Drive service whose role {@value #DRIVE_ROLE} grants folder {@value #DRIVE_FOLDER}.
 */
final class MemberListenerFixture {

    static final String DRIVE_ROLE = "111";
    static final String DRIVE_FOLDER = "fA";

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());

    final NerdBotConfig config = new NerdBotConfig();
    final FakeDiscordUserStore users = new FakeDiscordUserStore();
    final MemberQueue queue = new MemberQueue(users, Runnable::run);
    final GuildScope guildScope = new GuildScope(() -> config);
    final FakeDrivePermissionClient driveClient = new FakeDrivePermissionClient();
    final DrivePermissionService driveService = new DrivePermissionService(CIPHER, driveClient);
    final List<MessageEmbed> logged = new ArrayList<>();
    final LogChannel logChannel = logged::add;
    Optional<DrivePermissionService> drive = Optional.of(driveService);

    MemberListenerFixture() {
        config.setGuildId(MAIN_GUILD_ID);
        GoogleDriveConfig driveConfig = config.getGoogleDriveConfig();
        driveConfig.setEnabled(true);
        DriveFolderMapping mapping = new DriveFolderMapping();
        mapping.setRoleId(DRIVE_ROLE);
        mapping.setFolderIds(List.of(DRIVE_FOLDER));
        mapping.setAccessLevel("READER");
        driveConfig.setFolderMappings(List.of(mapping));
    }

    /** A stored member who linked their Drive while holding {@code roleIds}. */
    DiscordUser linkedUser(String id, List<String> roleIds) {
        DiscordUser user = new DiscordUser(id);
        user.setRoleIds(roleIds);
        new DriveLinkWorkflow(driveService, config.getGoogleDriveConfig()).link(user, id + "@example.com", roleIds, List.of(user));
        users.seed(user);
        return user;
    }

    List<String> fieldNames(MessageEmbed embed) {
        return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
    }
}
