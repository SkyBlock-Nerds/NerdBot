package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.modmail.ModMailService;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

import java.util.Optional;

@Slf4j
public class ModMailCommands extends ApplicationCommand {

    @JDASlashCommand(name = "modmail", subcommand = "find", description = "Find a Mod Mail thread", defaultLocked = true)
    public void findModMailThread(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());
        ModMailService modMailService = ModMailService.getInstance();

        modMailService.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = modMailService.findExistingThread(member.getUser());
            if (modMailThread.isEmpty()) {
                TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.thread_not_found", member.getAsMention());
                return;
            }

            TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.thread_found", member.getAsMention(), modMailThread.get().getAsMention());
        }, () -> TranslationManager.edit(event.getHook(), discordUser, "commands.mod_mail.channel_not_found"));
    }

    @JDASlashCommand(name = "modmail", subcommand = "new", description = "Create a new Mod Mail thread for the specified member", defaultLocked = true)
    public void createNewModMail(GuildSlashEvent event, @AppOption Member member) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.edit(event.getHook(), "database.not_connected");
            return;
        }

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());
        ModMailService modMailService = ModMailService.getInstance();

        modMailService.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = modMailService.findExistingThread(member.getUser());
            if (modMailThread.isPresent()) {
                TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.already_exists", member.getAsMention(), modMailThread.get().getAsMention());
                return;
            }

            try {
                ThreadChannel thread = modMailService.createNewThread(member.getUser(), event.getUser());
                TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.created", member.getAsMention(), thread.getAsMention());
            } catch (IllegalStateException e) {
                log.error("Failed to create mod mail thread", e);
                TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.channel_not_found");
            }
        }, () -> TranslationManager.edit(event.getHook(), commandSender, "commands.mod_mail.channel_not_found"));
    }
}
