package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.drive.DriveLinkWorkflow;
import net.hypixel.nerdbot.app.drive.DriveLogEmbeds;
import net.hypixel.nerdbot.app.drive.DrivePermissionService;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.drive.DriveGrant;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Creates a member's record when they join and removes it when they leave. Leaving is one queued
 * step, so their Drive access is revoked before the record holding the permission ids goes.
 */
@Slf4j
public class MemberLifecycleListener {

    private final GuildScope guildScope;
    private final MemberQueue queue;
    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;
    private final Provider<Optional<DrivePermissionService>> drive;
    private final LogChannel logChannel;

    @Inject
    public MemberLifecycleListener(GuildScope guildScope, MemberQueue queue, DiscordUserStore users, Provider<NerdBotConfig> config,
                                   Provider<Optional<DrivePermissionService>> drive, LogChannel logChannel) {
        this.guildScope = guildScope;
        this.queue = queue;
        this.users = users;
        this.config = config;
        this.drive = drive;
        this.logChannel = logChannel;
    }

    @SubscribeEvent
    public void onMemberJoin(GuildMemberJoinEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && !event.getUser().isBot()) {
            join(event.getMember());
        }
    }

    @SubscribeEvent
    public void onMemberRemove(GuildMemberRemoveEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && !event.getUser().isBot()) {
            leave(event.getUser().getId());
        }
    }

    CompletableFuture<Void> join(Member member) {
        String memberId = member.getId();
        log.info("Member {} ({}) joined", memberId, member.getUser().getName());

        return queue.run(memberId, "arrival", () -> {
            if (users.findById(memberId).isPresent()) {
                log.warn("Member {} joined but already has a record; keeping it", memberId);
                return;
            }

            DiscordUser user = new DiscordUser(memberId);
            user.setRoleIds(member.getRoles().stream().map(Role::getId).toList());
            users.save(user).join();
            log.info("Created the record for member {}", memberId);
        });
    }

    CompletableFuture<Void> leave(String memberId) {
        log.info("Member {} left", memberId);

        return queue.run(memberId, "departure", () -> {
            users.findById(memberId).ifPresent(this::revokeDriveAccess);
            users.delete(memberId).join();
            log.info("Removed the record for member {}", memberId);
        });
    }

    private void revokeDriveAccess(DiscordUser user) {
        Optional<DrivePermissionService> service = drive.get();
        if (user.getDriveAccess() == null || service.isEmpty() || !config.get().getGoogleDriveConfig().isEnabled()) {
            return;
        }

        List<String> heldFolders = user.getDriveAccess().getGrants().stream().map(DriveGrant::folderId).toList();
        new DriveLinkWorkflow(service.get(), config.get().getGoogleDriveConfig()).revokeAndForget(user);
        log.info("Revoked Drive access for departing member {}", user.getDiscordId());
        DriveLogEmbeds.postAccessChange(logChannel, service.get(), user.getDiscordId(), List.of(), heldFolders);
    }
}
