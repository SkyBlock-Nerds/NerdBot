package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.JsonArray;
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
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.curator.ForumGreenlitChannelCurator;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.TimeUtil;
import net.hypixel.nerdbot.util.Util;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

@Log4j2
public class ExportCommands extends ApplicationCommand {

    private static final String PARENT_COMMAND = "export";

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "threads", description = "Export threads from a Forum Channel", defaultLocked = true)
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
            List<MessageReaction> reactions = startMessage.getReactions().stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .toList();

            int agrees = 0;
            int disagrees = 0;

            if (!reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("agree")).toList().isEmpty()) {
                agrees = reactions.stream()
                    .filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("agree"))
                    .toList()
                    .get(0)
                    .getCount();
            }

            if (!reactions.stream().filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("disagree")).toList().isEmpty()) {
                disagrees = reactions.stream()
                    .filter(messageReaction -> messageReaction.getEmoji().getName().equalsIgnoreCase("disagree"))
                    .toList()
                    .get(0)
                    .getCount();
            }

            stringBuilder.append("\n").append(username).append(";")
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

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "greenlit", description = "Exports all greenlit forum posts into a CSV file", defaultLocked = true)
    public void exportGreenlitThreads(GuildSlashEvent event, @AppOption ForumChannel channel, @Optional @AppOption(description = "Disregards any post before this UNIX timestamp (Default: 0)") long suggestionsAfter) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            TranslationManager.reply(event, "generic.user_not_found");
            return;
        }

        if (!NerdBotApp.getBot().getDatabase().isConnected()) {
            TranslationManager.reply(event, discordUser, "database.not_connected");
            log.error("Couldn't connect to the database!");
            return;
        }

        Curator<ForumChannel, ThreadChannel> forumChannelCurator = new ForumGreenlitChannelCurator(true);
        NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
            event.deferReply(true).complete();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
                        if (forumChannelCurator.isCompleted()) {
                            this.cancel();
                            return;
                        }

                        if (forumChannelCurator.getCurrentObject() == null) {
                            return;
                        }

                        event.getHook().editOriginal("Export progress: " + forumChannelCurator.getIndex() + "/" + forumChannelCurator.getTotal()
                            + " in " + TimeUtil.formatMsCompact(System.currentTimeMillis() - forumChannelCurator.getStartTime()) + "ms"
                            + "\nCurrently looking at " + forumChannelCurator.getCurrentObject().getJumpUrl()
                        ).queue();
                    });
                }
            };

            Timer timer = new Timer();
            timer.scheduleAtFixedRate(task, 0, 1_000);

            List<GreenlitMessage> output = forumChannelCurator.curate(channel);

            if (output.isEmpty()) {
                TranslationManager.edit(event.getHook(), discordUser, "curator.no_greenlit_messages");
                return;
            }

            StringBuilder csvOutput = new StringBuilder();
            for (GreenlitMessage greenlitMessage : output) {
                // If we manually limited the timestamps to before "x" time (defaults to 0 btw) it "removes" the greenlit suggestions from appearing in the linked CSV file.
                if (greenlitMessage.getSuggestionTimestamp() >= suggestionsAfter) {
                    // The Format is shown below, Tabs (\t) are the separators between values, as commas cannot be used, but It's still in the CSV file format due to Google Sheets Default Import only accepting CSV files.
                    // Timestamp Posted, Tags, Suggestion Title (Hyperlinked to the post), Reserved Location, Reserved Location, Reserved Location, Reserved Location, Reserved Location
                    csvOutput.append("=EPOCHTODATE(").append(greenlitMessage.getSuggestionTimestamp() / 1_000L).append(")\t")
                        .append(String.join(", ", greenlitMessage.getTags()))
                        .append("\t=HYPERLINK(\"").append(greenlitMessage.getSuggestionUrl()).append("\", \"").append(greenlitMessage.getSuggestionTitle()).append("\")")
                        .append("\t\t\t\t\t\t")
                        .append("\n");
                }
            }

            String csvString = csvOutput.toString();
            String fileName = String.format(channel.getName() + "-%s.csv", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("ECT", ZoneId.SHORT_IDS)).format(Instant.now()));

            try {
                event.getHook().sendMessage(TranslationManager.translate("curator.greenlit_import_instructions", discordUser)).setEphemeral(true).addFiles(FileUpload.fromData(Util.createTempFile(fileName, csvString))).queue();
            } catch (IOException exception) {
                TranslationManager.edit(event.getHook(), discordUser, "commands.temp_file_error", exception.getMessage());
                log.error("Failed to create temp file!", exception);
                log.error("File contents:\n" + csvString);
            }
        });
    }

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "list", description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.", defaultLocked = true)
    public void userList(GuildSlashEvent event, @Optional @AppOption(description = "Comma-separated role names to search for (Default: Member)") String roles) throws IOException {
        event.deferReply(true).complete();
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        String[] roleArray = roles != null ? roles.split(", ?") : new String[]{"Member"};
        JsonArray uuidArray = new JsonArray();

        List<MojangProfile> profiles = event.getGuild().loadMembers().get()
            .stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> RoleManager.hasAnyRole(member, roleArray))
            .map(member -> discordUserRepository.findById(member.getId()))
            .filter(DiscordUser::isProfileAssigned)
            .map(DiscordUser::getMojangProfile)
            .toList();

        log.info("Found " + profiles.size() + " members meeting requirements.");
        profiles.forEach(profile -> uuidArray.add(profile.getUniqueId().toString()));
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(uuidArray));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }
}
