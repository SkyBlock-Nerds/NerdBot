package net.hypixel.skyblocknerds.discordbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;

@Log4j2
public class ActivityListener extends ListenerAdapter {

    private final DiscordUserRepository discordUserRepository;

    public ActivityListener() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        // TODO implement message activity tracking
        Message message = event.getMessage();
    }

    @SubscribeEvent
    public void onMessageUpdate(MessageUpdateEvent event) {
        // TODO implement message activity tracking
        Message message = event.getMessage();
    }
}
