package net.hypixel.nerdbot.app.profile;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Links a Minecraft account to a member: saves it, then sets their nickname and moves them out of limbo. */
@Slf4j
public class ProfileLinker {

    private final DiscordUserStore users;
    private final Provider<NerdBotConfig> config;

    @Inject
    public ProfileLinker(DiscordUserStore users, Provider<NerdBotConfig> config) {
        this.users = users;
        this.config = config;
    }

    /**
     * Completes once the profile is saved. The nickname and role changes come after and only log
     * when Discord refuses them, so a link never fails over a missing permission.
     */
    public CompletableFuture<Void> link(Member member, MojangProfile profile) {
        DiscordUser user;
        try {
            user = users.require(member.getId());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        user.setMojangProfile(profile);
        return users.save(user).thenRun(() -> {
            rename(member, profile.getUsername());
            promote(member);
        });
    }

    private static void rename(Member member, String username) {
        if (member.getEffectiveName().toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            return;
        }

        try {
            member.modifyNickname(username).queue(null, failure -> log.warn("Couldn't rename {} to {}", member.getId(), username, failure));
        } catch (PermissionException exception) {
            log.warn("Couldn't rename {} to {}: {}", member.getId(), username, exception.getMessage());
        }
    }

    private void promote(Member member) {
        RoleConfig roles = config.get().getRoleConfig();
        String newMemberRoleId = roles.getNewMemberRoleId();
        if (newMemberRoleId == null || newMemberRoleId.isBlank()) {
            return;
        }

        Guild guild = member.getGuild();
        Role newMemberRole = guild.getRoleById(newMemberRoleId);
        if (newMemberRole == null) {
            log.warn("The new member role {} does not exist", newMemberRoleId);
            return;
        }

        // Members who already rank at or above it are past the new member stage
        if (RoleManager.hasHigherOrEqualRole(member, newMemberRole)) {
            return;
        }

        try {
            guild.addRoleToMember(member, newMemberRole).queue(
                added -> leaveLimbo(guild, member, roles.getLimboRoleId()),
                failure -> log.warn("Couldn't give {} the new member role", member.getId(), failure));
        } catch (PermissionException exception) {
            log.warn("Couldn't give {} the new member role: {}", member.getId(), exception.getMessage());
        }
    }

    private static void leaveLimbo(Guild guild, Member member, String limboRoleId) {
        if (limboRoleId == null || limboRoleId.isBlank()) {
            return;
        }

        Role limboRole = guild.getRoleById(limboRoleId);
        if (limboRole == null) {
            log.warn("The limbo role {} does not exist", limboRoleId);
            return;
        }

        try {
            guild.removeRoleFromMember(member, limboRole).queue(null, failure -> log.warn("Couldn't take the limbo role from {}", member.getId(), failure));
        } catch (PermissionException exception) {
            log.warn("Couldn't take the limbo role from {}: {}", member.getId(), exception.getMessage());
        }
    }
}
