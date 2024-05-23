package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;

public class PersonalCommands extends ApplicationCommand {

    private static final String COMMAND_NAME = "my";

    private final DiscordUserRepository discordUserRepository;

    public PersonalCommands() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = COMMAND_NAME, subcommand = "profile", description = "View your profile")
    public void viewPersonalProfile(GuildSlashEvent event) {
        discordUserRepository.findById(event.getMember().getId()).ifPresentOrElse(user -> {
            event.reply("Your profile: " + user).queue();
        }, () -> {
            event.reply("Couldn't find your profile!").queue();
        });
    }
}
