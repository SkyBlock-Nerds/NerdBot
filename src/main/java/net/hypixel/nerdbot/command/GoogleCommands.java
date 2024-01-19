package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.Util;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Log4j2
public class GoogleCommands extends ApplicationCommand {

    @JDASlashCommand(name = "export", subcommand = "threads", description = "Export Shen Threads", defaultLocked = true)
    public void exportShenThreads(GuildSlashEvent event, @AppOption ForumChannel forumChannel) {
        event.deferReply(true).queue();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());

        TranslationManager.edit(event.getHook(), commandSender, "commands.export.exporting_threads", forumChannel.getAsMention());

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Username;Item;Summary;Agree;Disagree");

        Stream<ThreadChannel> threads = Util.safeArrayStream(forumChannel.getThreadChannels().toArray(), forumChannel.retrieveArchivedPublicThreadChannels().stream().toArray())
            .map(ThreadChannel.class::cast)
            .distinct()
            .sorted((o1, o2) -> (int) (o1.getTimeCreated().toEpochSecond() - o2.getTimeCreated().toEpochSecond()));

        // Filter out threads that don't have a start message
        threads = threads.filter(threadChannel -> {
            try {
                Message startMessage = threadChannel.retrieveStartMessage().complete();
                return startMessage != null && !startMessage.getContentRaw().isBlank();
            } catch (ErrorResponseException exception) {
                return exception.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE;
            }
        });

        List<ThreadChannel> threadList = threads.toList();
        for (ThreadChannel threadChannel : threadList) {
            DiscordUser discordUser = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class).findById(threadChannel.getOwnerId());
            String username;

            if (discordUser == null || discordUser.noProfileAssigned()) {
                Member owner = threadChannel.getOwner() == null ? threadChannel.getGuild().retrieveMemberById(threadChannel.getOwnerId()).complete() : threadChannel.getOwner();
                username = owner.getEffectiveName();
            } else {
                username = discordUser.getMojangProfile().getUsername();
            }

            int index = threadList.indexOf(threadChannel);
            TranslationManager.edit(event.getHook(), commandSender, "commands.export.exporting_thread", index + 1, threadList.size(), threadChannel.getName(), username);

            Message startMessage = threadChannel.retrieveStartMessage().complete();
            List<MessageReaction> reactions = startMessage.getReactions()
                .stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .toList();

            int agrees = 0;
            int disagrees = 0;

            if (!reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("agree")).toList().isEmpty()) {
                agrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("agree")).toList().get(0).getCount();
            }

            if (!reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("disagree")).toList().isEmpty()) {
                disagrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("disagree")).toList().get(0).getCount();
            }

            stringBuilder.append("\n")
                .append(username).append(";")
                .append("=HYPERLINK(\"").append(threadChannel.getJumpUrl()).append("\", \"").append(threadChannel.getName()).append("\");")
                .append("\"").append(startMessage.getContentRaw().replace("\"", "\"\"")).append("\"").append(";")
                .append(agrees).append(";")
                .append(disagrees);

            TranslationManager.edit(event.getHook(), commandSender, "commands.export.exported_thread", index + 1, threadList.size(), threadChannel.getName(), username);

            // Check if all threads have been exported
            if ((index + 1) == threadList.size()) {
                try {
                    File file = Util.createTempFile("threads-" + forumChannel.getName() + "-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()) + ".csv", stringBuilder.toString());
                    event.getHook().editOriginal(TranslationManager.translate("commands.export.complete", forumChannel.getAsMention())).setFiles(FileUpload.fromData(file)).queue();
                } catch (IOException exception) {
                    log.error("Failed to create temp file!", exception);
                    TranslationManager.edit(event.getHook(), commandSender, "commands.temp_file_error", exception.getMessage());
                }
            }
        }
    }
}
