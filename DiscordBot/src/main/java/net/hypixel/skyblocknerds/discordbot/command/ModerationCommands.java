package net.hypixel.skyblocknerds.discordbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.skyblocknerds.database.objects.user.warning.WarningEntry;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;

public class ModerationCommands extends ApplicationCommand {

    private final DiscordUserRepository discordUserRepository;

    public ModerationCommands() {
        this.discordUserRepository = RepositoryManager.getInstance().getRepository(DiscordUserRepository.class);
    }

    @JDASlashCommand(name = "warn", description = "Issue an official warning to a member", defaultLocked = true)
    public void warnMember(GuildSlashEvent event, @AppOption(description = "The member to warn") Member member, @AppOption(description = "The reason for the warning") String reason) {
        event.deferReply(true).complete();

        discordUserRepository.findById(member.getId()).ifPresentOrElse(discordUser -> {
            discordUser.getWarnings().add(new WarningEntry(reason, System.currentTimeMillis(), event.getMember().getId()));
            event.getHook().editOriginal("Warned " + member.getAsMention() + " for `" + reason + "`").queue();
            // TODO log the warning in a channel
        }, () -> event.reply("Couldn't find that member in the database!").queue());
    }
}
