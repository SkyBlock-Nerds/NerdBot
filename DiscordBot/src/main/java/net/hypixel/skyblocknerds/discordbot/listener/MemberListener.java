package net.hypixel.skyblocknerds.discordbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.utilities.StringUtilities;

@Log4j2
public class MemberListener extends ListenerAdapter {

    private final DiscordUserRepository discordUserRepository;

    public MemberListener() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @SubscribeEvent
    public void onUserUpdateGlobalNameEvent(UserUpdateNameEvent event) {
        discordUserRepository.findById(event.getUser().getId()).ifPresent(discordUser -> {
            discordUser.setLastKnownUsername(event.getNewName());
            log.info("Updated last known username for user " + discordUser.getDiscordId() + " to " + discordUser.getLastKnownUsername());
        });
    }

    @SubscribeEvent
    public void onUserUpdateNicknameEvent(GuildMemberUpdateNicknameEvent event) {
        discordUserRepository.findById(event.getMember().getId()).ifPresent(discordUser -> {
            if (!discordUser.hasMinecraftProfile()) {
                return;
            }

            boolean shouldUpdate = event.getNewNickname() == null || !event.getNewNickname().contains(discordUser.getMinecraftProfile().getUsername());

            if (shouldUpdate) {
                try {
                    event.getMember().modifyNickname(discordUser.getMinecraftProfile().getUsername()).complete();
                    log.info("Updated nickname for user " + StringUtilities.formatNameWithId(event.getUser().getName(), event.getMember().getId()) + " to " + discordUser.getMinecraftProfile().getUsername());
                } catch (HierarchyException exception) {
                    log.error("Failed to update nickname for user " + StringUtilities.formatNameWithId(event.getUser().getName(), event.getMember().getId()) + " since they have a higher role than the bot");
                } catch (Exception exception) {
                    log.error("Failed to update nickname for user " + StringUtilities.formatNameWithId(event.getUser().getName(), event.getMember().getId()), exception);
                }
            }
        });
    }
}
