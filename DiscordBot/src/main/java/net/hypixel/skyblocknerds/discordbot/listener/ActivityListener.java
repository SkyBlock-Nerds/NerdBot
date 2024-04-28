package net.hypixel.skyblocknerds.discordbot.listener;

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;

public class ActivityListener extends ListenerAdapter {

    private final DiscordUserRepository discordUserRepository;

    public ActivityListener() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }
}
