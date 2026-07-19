package net.hypixel.nerdbot.app.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.role.RoleIdSync;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.util.List;

/**
 * Mirrors Discord role changes onto the user's document as they happen. The
 * Minecraft network reads roleIds from Mongo for whitelist/rank decisions, so
 * changes are persisted immediately instead of waiting for the autosave flush.
 */
@Slf4j
public class RoleSyncListener {

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        syncRoles(event.getMember());
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        syncRoles(event.getMember());
    }

    private void syncRoles(Member member) {
        if (member.getUser().isBot()) {
            return;
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        List<String> roleIds = member.getRoles().stream().map(Role::getId).toList();

        repository.findByIdAsync(member.getId()).thenAccept(user -> {
            if (user == null) {
                return; // unverified members are created by the grabber/activity flows
            }
            if (RoleIdSync.applyRoleIds(user, roleIds)) {
                repository.cacheObject(user);
                repository.saveToDatabaseAsync(user);
                log.info("Updated role ids for {} ({} roles)", member.getId(), roleIds.size());
            }
        });
    }
}
