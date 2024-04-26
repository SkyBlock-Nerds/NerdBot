package net.hypixel.skyblocknerds.discordbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;

@Log4j2
public class MemberListener {

    @SubscribeEvent
    public void onUserUpdateGlobalName(UserUpdateGlobalNameEvent event) {
        DiscordUserRepository repository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
        repository.findById(event.getUser().getId()).ifPresent(discordUser -> {
            discordUser.setLastKnownUsername(event.getNewGlobalName());
            log.info("Updated last known username for user " + discordUser.getDiscordId() + " to " + discordUser.getLastKnownUsername());
        });
    }
}
