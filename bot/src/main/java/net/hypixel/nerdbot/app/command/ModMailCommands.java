package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.discord.database.model.user.DiscordUser;
import net.hypixel.nerdbot.app.modmail.ModMailService;
import net.hypixel.nerdbot.discord.database.model.repository.DiscordUserRepository;

import java.util.Optional;

@Slf4j
public class ModMailCommands {

    @SlashCommand(name = "modmail", subcommand = "find", description = "Find a Mod Mail thread", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void findModMailThread(SlashCommandInteractionEvent event, @SlashOption Member member) {
        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findOrCreateById(event.getMember().getId());
        ModMailService modMailService = ModMailService.getInstance();

        modMailService.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = modMailService.findExistingThread(member.getUser());
            if (modMailThread.isEmpty()) {
                event.getHook().editOriginal(String.format("Could not find a Mod Mail thread for %s!", member.getAsMention())).queue();
                return;
            }

            event.getHook().editOriginal(String.format("Found Mod Mail thread for %s: %s", member.getAsMention(), modMailThread.get().getAsMention())).queue();
        }, () -> event.getHook().editOriginal("Could not find the Mod Mail channel! Is there one configured?").queue());
    }

    @SlashCommand(name = "modmail", subcommand = "new", description = "Create a new Mod Mail thread for the specified member", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void createNewModMail(SlashCommandInteractionEvent event, @SlashOption Member member) {
        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());
        ModMailService modMailService = ModMailService.getInstance();

        modMailService.getModMailChannel().ifPresentOrElse(forumChannel -> {
            Optional<ThreadChannel> modMailThread = modMailService.findExistingThread(member.getUser());
            if (modMailThread.isPresent()) {
                event.getHook().editOriginal(String.format("A Mod Mail thread for %s already exists: %s", member.getAsMention(), modMailThread.get().getAsMention())).queue();
                return;
            }

            try {
                ThreadChannel thread = modMailService.createNewThread(member.getUser(), event.getUser());
                event.getHook().editOriginal(String.format("Created new Mod Mail thread for %s: %s", member.getAsMention(), thread.getAsMention())).queue();
            } catch (IllegalStateException e) {
                log.error("Failed to create mod mail thread", e);
                event.getHook().editOriginal("Could not find the Mod Mail channel! Is there one configured?").queue();
            }
        }, () -> event.getHook().editOriginal("Could not find the Mod Mail channel! Is there one configured?").queue());
    }
}