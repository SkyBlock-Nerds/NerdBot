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
import net.dv8tion.jda.api.entities.ThreadMember;
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
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.curator.ForumGreenlitChannelCurator;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.TimeUtil;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.csv.CSVData;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ExportCommands extends ApplicationCommand {

    private static final String PARENT_COMMAND = "export";

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "threads", description = "Export threads from a Forum Channel", defaultLocked = true)
    public void exportForumThreads(GuildSlashEvent event, @AppOption ForumChannel forumChannel) {
        event.deferReply(true).queue();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());

        TranslationManager.edit(event.getHook(), commandSender, "commands.export.exporting_threads", forumChannel.getAsMention());

        CSVData csvData = new CSVData(List.of("Username", "Thread", "Content", "Agrees", "Disagrees"), ";");
        List<ThreadChannel> threads = Util.safeArrayStream(forumChannel.getThreadChannels().toArray(), forumChannel.retrieveArchivedPublicThreadChannels().stream().toArray())
            .map(ThreadChannel.class::cast)
            .distinct()
            .sorted((o1, o2) -> (int) (o1.getTimeCreated().toEpochSecond() - o2.getTimeCreated().toEpochSecond()))
            .filter(threadChannel -> {
                try {
                    Message startMessage = threadChannel.retrieveStartMessage().complete();
                    return startMessage != null && !startMessage.getContentRaw().isBlank();
                } catch (ErrorResponseException exception) {
                    return exception.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE;
                }
            })
            .toList();

        for (ThreadChannel threadChannel : threads) {
            DiscordUser discordUser = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class).findById(threadChannel.getOwnerId());
            String username;

            try {
                if (discordUser != null && discordUser.isProfileAssigned()) {
                    username = discordUser.getMojangProfile().getUsername();
                } else {
                    ThreadMember threadOwner = threadChannel.getOwnerThreadMember() == null
                        ? threadChannel.retrieveThreadMemberById(threadChannel.getOwnerId()).completeAfter(3, TimeUnit.SECONDS)
                        : threadChannel.getOwnerThreadMember();
                    username = threadOwner.getMember().getEffectiveName();
                }
            } catch (Exception exception) {
                username = threadChannel.getOwnerId();
                log.error("Failed to get username for thread owner " + threadChannel.getOwnerId(), exception);
            }

            int index = threads.indexOf(threadChannel);
            TranslationManager.edit(event.getHook(), commandSender, "commands.export.exporting_thread", index + 1, threads.size(), threadChannel.getName(), username);

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

            csvData.addRow(List.of(
                username,
                "=HYPERLINK(\"" + threadChannel.getName().replace("\"", "\"\"") + "\")",
                "\"" + startMessage.getContentRaw().replace("\"", "\"\"") + "\"",
                String.valueOf(agrees),
                String.valueOf(disagrees)
            ));

            TranslationManager.edit(event.getHook(), commandSender, "commands.export.exported_thread", index + 1, threads.size(), threadChannel.getName(), username);

            // Check if all threads have been exported
            if ((index + 1) == threads.size()) {
                try {
                    File file = Util.createTempFile("export-threads-" + forumChannel.getName() + "-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()) + ".csv", csvData.toCSV());
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

            CSVData csvData = new CSVData(List.of("Creation Date", "Tags", "Title"), ";");

            for (GreenlitMessage greenlitMessage : output) {
                // If we manually limited the timestamps to before "x" time (defaults to 0) it "removes" the greenlit suggestions from appearing in the linked CSV file.
                if (greenlitMessage.getSuggestionTimestamp() >= suggestionsAfter) {
                    csvData.addRow(List.of(
                        formatTimestamp(greenlitMessage.getSuggestionTimestamp() / 1_000L),
                        "\"" + String.join(", ", greenlitMessage.getTags()) + "\"",
                        "=HYPERLINK(\"" + greenlitMessage.getSuggestionUrl() + "\", \"" + greenlitMessage.getSuggestionTitle().replace("\"", "\"\"") + "\")"
                    ));
                }
            }

            try {
                event.getHook().sendMessage(TranslationManager.translate("curator.greenlit_import_instructions", discordUser))
                    .setEphemeral(true)
                    .addFiles(FileUpload.fromData(Util.createTempFile(String.format("export-greenlit-" + channel.getName() + "-%s.csv", Util.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV())))
                    .queue();
            } catch (IOException exception) {
                TranslationManager.edit(event.getHook(), discordUser, "commands.temp_file_error", exception.getMessage());
                log.error("Failed to create temp file!", exception);
            }
        });
    }

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "uuids", description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.", defaultLocked = true)
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
        File file = Util.createTempFile(String.format("export-uuids-%s.csv", Util.FILE_NAME_DATE_FORMAT.format(Instant.now())), NerdBotApp.GSON.toJson(uuidArray));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "roles", description = "Export a list of users with the given roles", defaultLocked = true)
    public void exportRoles(GuildSlashEvent event, @AppOption(description = "Comma-separated list of role names (e.g. Role 1, Role 2, Role 3)") String roles) {
        event.deferReply(true).complete();

        String[] roleArray = roles.split(", ?");
        Map<String, List<String>> members = new HashMap<>();

        // Group roles as key to a list of members with that role
        event.getGuild().loadMembers().get().forEach(member -> {
            List<String> memberRoles = member.getRoles().stream().map(role -> role.getName().toLowerCase()).toList();
            for (String role : roleArray) {
                if (memberRoles.contains(role.toLowerCase())) {
                    members.computeIfAbsent(role, k -> new ArrayList<>()).add(member.getEffectiveName());
                }
            }
        });

        if (members.values().stream().allMatch(List::isEmpty)) {
            TranslationManager.edit(event.getHook(), "commands.export.none_found");
            return;
        }

        try {
            StringBuilder stringBuilder = new StringBuilder();

            for (Map.Entry<String, List<String>> entry : members.entrySet()) {
                stringBuilder.append(entry.getKey()).append(":\n");
                entry.getValue().forEach(member -> stringBuilder.append(member).append("\n"));
                stringBuilder.append("\n");
            }

            File file = Util.createTempFile(String.format("export-roles-%s.csv", Util.FILE_NAME_DATE_FORMAT.format(Instant.now())), stringBuilder.toString());
            event.getHook().sendFiles(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            log.error("Failed to create temp file!", exception);
            TranslationManager.reply(event, "commands.temp_file_error", exception.getMessage());
        }
    }

    @JDASlashCommand(name = PARENT_COMMAND, subcommand = "member-activity", description = "Export a list of members and their activity", defaultLocked = true)
    public void exportMemberActivity(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        CSVData csvData = new CSVData(List.of(
            "Discord Username",
            "Minecraft Username",
            "Last Global Activity",
            "Last VC Join",
            "Last Item Generation",
            "Last Suggestion Created",
            "Last Project Suggestion Created",
            "Last Alpha Suggestion Created",
            "Last Suggestion Vote",
            "Last Project Suggestion Vote",
            "Last Alpha Suggestion Vote",
            "Last Project Activity",
            "Last Alpha Activity",
            "Total Tracked Messages",
            "Messages Sent Recently",
            "Reviewed",
            "Comments"
        ), ";");

        if (discordUserRepository.isEmpty()) {
            TranslationManager.edit(event.getHook(), "commands.export.none_found");
            return;
        }

        List<DiscordUser> discordUsers = discordUserRepository.getAll();
        long inactivityTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(NerdBotApp.getBot().getConfig().getInactivityDays());

        discordUsers.removeIf(discordUser -> {
            Member member = event.getGuild().getMemberById(discordUser.getDiscordId());
            return member != null && RoleManager.hasAnyRole(member, Util.SPECIAL_ROLES);
        });

        discordUsers.removeIf(discordUser -> {
            LastActivity lastActivity = discordUser.getLastActivity();
            return lastActivity.getChannelActivityHistory().stream()
                .filter(channelActivityEntry -> !Arrays.asList(NerdBotApp.getBot().getConfig().getChannelConfig().getBlacklistedChannels()).contains(channelActivityEntry.getChannelId()))
                .anyMatch(entry -> entry.getLastMessageTimestamp() > inactivityTimestamp && entry.getMessageCount() > NerdBotApp.getBot().getConfig().getInactivityMessages());
        });

        discordUsers.forEach(discordUser -> {
            Member member = event.getGuild().getMemberById(discordUser.getDiscordId());

            if (member == null) {
                log.warn("[Member Activity Export] Member not found for user: " + discordUser.getDiscordId());
                return;
            }

            LastActivity lastActivity = discordUser.getLastActivity();

            String channelActivity = lastActivity.getChannelActivityHistory(NerdBotApp.getBot().getConfig().getInactivityDays())
                .stream()
                .filter(entry -> !Arrays.asList(NerdBotApp.getBot().getConfig().getChannelConfig().getBlacklistedChannels()).contains(entry.getChannelId()))
                .map(entry -> "#" + entry.getLastKnownDisplayName() + ": " + entry.getMessageCount())
                .reduce((s1, s2) -> s1 + "\n" + s2)
                .orElse("N/A");

            csvData.addRow(List.of(
                member.getUser().getName(),
                discordUser.getMojangProfile().getUsername() == null ? "Not Linked" : discordUser.getMojangProfile().getUsername(),
                formatTimestamp(lastActivity.getLastGlobalActivity()),
                formatTimestamp(lastActivity.getLastVoiceChannelJoinDate()),
                formatTimestamp(lastActivity.getLastItemGenUsage()),
                lastActivity.getSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getSuggestionCreationHistory().get(0)),
                lastActivity.getProjectSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getProjectSuggestionCreationHistory().get(0)),
                lastActivity.getAlphaSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getAlphaSuggestionCreationHistory().get(0)),
                lastActivity.getSuggestionVoteHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getSuggestionVoteHistory().get(0)),
                lastActivity.getProjectSuggestionVoteHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getProjectSuggestionVoteHistory().get(0)),
                lastActivity.getAlphaSuggestionVoteHistory().isEmpty() ? "N/A" : formatTimestamp(lastActivity.getAlphaSuggestionVoteHistory().get(0)),
                formatTimestamp(lastActivity.getLastProjectActivity()),
                formatTimestamp(lastActivity.getLastAlphaActivity()),
                String.valueOf(lastActivity.getChannelActivity().values().stream().mapToInt(Integer::intValue).sum()),
                "\"" + channelActivity + "\"",
                "FALSE"
            ));
        });

        try {
            File file = Util.createTempFile(String.format("export-member-activity-%s.csv", Util.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
            event.getHook().sendFiles(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            log.error("Failed to create temp file!", exception);
            TranslationManager.reply(event, "commands.temp_file_error", exception.getMessage());
        }
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp < 0) {
            return "N/A";
        }

        return "=EPOCHTODATE(" + timestamp / 1_000 + ")";
    }
}
