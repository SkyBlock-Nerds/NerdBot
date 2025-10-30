package net.hypixel.nerdbot.discord.command;

import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.bot.SkyBlockNerdsBot;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.role.RoleManager;
import net.hypixel.nerdbot.util.ArrayUtils;
import net.hypixel.nerdbot.util.FileUtils;
import net.hypixel.nerdbot.util.Utils;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.util.csv.CSVData;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExportCommands {

    private static final String PARENT_COMMAND = "export";

    @SlashCommand(name = PARENT_COMMAND, subcommand = "threads", description = "Export threads from a Forum Channel", guildOnly = true, requiredPermissions = {"ADMINISTRATOR"})
    public void exportForumThreads(SlashCommandInteractionEvent event, @SlashOption ForumChannel forumChannel) {
        event.deferReply(true).queue();

        if (event.getMember() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser commandSender = discordUserRepository.findOrCreateById(event.getMember().getId());

        event.getHook().editOriginal(String.format("Exporting threads from %s...", forumChannel.getAsMention())).queue();

        CSVData csvData = new CSVData(List.of("Username", "Thread", "Content", "Agrees", "Disagrees"), ";");
        List<ThreadChannel> threads = ArrayUtils.safeArrayStream(forumChannel.getThreadChannels().toArray(), forumChannel.retrieveArchivedPublicThreadChannels().stream().toArray())
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
            DiscordUser discordUser = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class).findById(threadChannel.getOwnerId());
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
            event.getHook().editOriginal(String.format("Exporting thread %d/%d: %s by %s", index + 1, threads.size(), threadChannel.getName(), username)).queue();

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

            event.getHook().editOriginal(String.format("Finished exporting thread %d/%d: %s by %s", index + 1, threads.size(), threadChannel.getName(), username)).queue();

            // Check if all threads have been exported
            if ((index + 1) == threads.size()) {
                try {
                    File file = FileUtils.createTempFile("export-threads-" + forumChannel.getName() + "-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()) + ".csv", csvData.toCSV());
                    event.getHook().editOriginal(String.format("Finished exporting all threads from %s!", forumChannel.getAsMention())).setFiles(FileUpload.fromData(file)).queue();
                } catch (IOException exception) {
                    log.error("Failed to create temp file!", exception);
                    event.getHook().editOriginal(String.format("Failed to create temporary file: %s", exception.getMessage())).queue();
                }
            }
        }
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "greenlit", description = "Exports all greenlit forum posts into a CSV file", guildOnly = true, requiredPermissions = {"MANAGE_CHANNEL", "MANAGE_THREADS"})
    public void exportGreenlitThreads(SlashCommandInteractionEvent event, @SlashOption(description = "Disregards any post before this UNIX timestamp (Default: 0)", required = false) long suggestionsAfter) {
        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            log.error("Couldn't connect to the database!");
            return;
        }

        if (event.getMember() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(event.getMember().getId());

        if (discordUser == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        GreenlitMessageRepository greenlitMessageRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class);
        // Force a save before exporting because some might still be cached
        greenlitMessageRepository.saveAllToDatabase();

        List<GreenlitMessage> output = greenlitMessageRepository.getAllDocuments();

        if (output.isEmpty()) {
            log.info("No greenlit suggestions found for " + event.getMember().getEffectiveName() + "'s export (after: " + formatTimestampLog(suggestionsAfter) + ", current time: " + formatTimestampLog(System.currentTimeMillis()) + ")");
            event.getHook().editOriginal("No suggestions were greenlit").queue();
            return;
        }

        // Send a log line with formatted dates for the suggestionsAfter parameter
        log.info("Exporting " + output.size() + " greenlit suggestions for " + event.getMember().getEffectiveName() + " (after: " + formatTimestampLog(suggestionsAfter) + ", current time: " + formatTimestampLog(System.currentTimeMillis()) + ")");

        CSVData csvData = new CSVData(List.of("Creation Date", "Tags", "Title"), ";");

        for (GreenlitMessage greenlitMessage : output) {
            // If we manually limited the timestamps to before "x" time (defaults to 0) it "removes" the greenlit suggestions from appearing in the linked CSV file.
            if (greenlitMessage.getSuggestionTimestamp() >= suggestionsAfter) {
                csvData.addRow(List.of(
                    formatTimestampSheets(greenlitMessage.getSuggestionTimestamp()),
                    "\"" + String.join(", ", greenlitMessage.getTags()) + "\"",
                    "=HYPERLINK(\"" + greenlitMessage.getSuggestionUrl() + "\", \"" + greenlitMessage.getSuggestionTitle().replace("\"", "\"\"") + "\")"
                ));
                log.info("Added greenlit suggestion '" + greenlitMessage.getSuggestionTitle() + "' to the greenlit suggestion export for " + event.getMember().getEffectiveName() + " (after: " + formatTimestampLog(suggestionsAfter) + ", current time: " + formatTimestampLog(System.currentTimeMillis()) + ")");
            } else {
                log.debug("Skipping greenlit suggestion " + greenlitMessage.getSuggestionTitle() + " because it was created before the specified timestamp (after: " + formatTimestampLog(suggestionsAfter) + ", suggestion timestamp: " + formatTimestampLog(greenlitMessage.getSuggestionTimestamp()) + ")");
            }
        }

        if (!csvData.hasContent()) {
            log.info("No greenlit suggestions found for " + event.getMember().getEffectiveName() + "'s export (after: " + formatTimestampLog(suggestionsAfter) + ")");
            event.getHook().editOriginal("No suggestions were greenlit").queue();
            return;
        }

        try {
            MessageEditData data = MessageEditBuilder.from(MessageEditData.fromContent("To import into Google Sheets, go to File -> Import, Upload the `.csv` document shown below.\nChange `Import Location` to `Append to current sheet` and `Separator Type` should be defaulted to Automatic detection if not, change it to tabs.)"))
                .setFiles(FileUpload.fromData(FileUtils.createTempFile(String.format("export-greenlit-%s.csv", FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV())))
                .build();

            event.getHook().editOriginal(data).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal(String.format("Failed to create temporary file: %s", exception.getMessage())).queue();
            log.error("Failed to create temp file!", exception);
        }
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "uuids", description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.", guildOnly = true, requiredPermissions = {"ADMINISTRATOR"})
    public void userList(SlashCommandInteractionEvent event, @SlashOption(description = "Comma-separated role names to search for (Default: Member)", required = false) String roles) {
        event.deferReply(true).queue();
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        String[] roleArray = roles != null ? roles.split(", ?") : new String[]{"Member"};

        if (event.getGuild() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        event.getGuild().loadMembers().onSuccess(members -> {
            List<MojangProfile> profiles = members.stream()
                .filter(member -> !member.getUser().isBot())
                .filter(member -> RoleManager.hasAnyRole(member, roleArray))
                .map(member -> discordUserRepository.findById(member.getId()))
                .filter(Objects::nonNull)
                .filter(DiscordUser::isProfileAssigned)
                .map(DiscordUser::getMojangProfile)
                .toList();

            log.info("Found " + profiles.size() + " members meeting requirements.");

            if (profiles.isEmpty()) {
                event.getHook().editOriginal("Nothing found to export!").queue();
                return;
            }

            JsonArray uuidArray = new JsonArray();
            profiles.forEach(profile -> uuidArray.add(profile.getUniqueId().toString()));

            try {
                File file = FileUtils.createTempFile(
                    String.format("export-uuids-%s.json", FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())),
                    BotEnvironment.GSON.toJson(uuidArray)
                );
                event.getHook().sendFiles(FileUpload.fromData(file)).queue();
            } catch (IOException exception) {
                log.error("Failed to create temp file!", exception);
                event.getHook().editOriginal("An error occurred while creating the temp file: " + exception.getMessage()).queue();
            }
        }).onError(throwable -> {
            log.error("Failed to load guild members for UUID export", throwable);
            event.getHook().editOriginal("Failed to load guild members: " + throwable.getMessage()).queue();
        });
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "roles", description = "Export a list of users with the given roles", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void exportRoles(SlashCommandInteractionEvent event, @SlashOption(description = "Comma-separated list of role names (e.g. Role 1, Role 2, Role 3)") String roles) {
        event.deferReply(true).queue();
        String[] roleArray = roles.split(", ?");

        if (event.getGuild() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        event.getGuild().loadMembers().onSuccess(members -> {
            Map<String, List<String>> membersByRole = new HashMap<>();

            // Group roles as key to a list of members with that role
            members.forEach(member -> {
                List<String> memberRoles = member.getRoles().stream().map(role -> role.getName().toLowerCase()).toList();
                for (String role : roleArray) {
                    if (memberRoles.contains(role.toLowerCase())) {
                        membersByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(member.getEffectiveName());
                    }
                }
            });

            if (membersByRole.values().stream().allMatch(list -> list == null || list.isEmpty())) {
                event.getHook().editOriginal("Nothing found to export!").queue();
                return;
            }

            try {
                StringBuilder stringBuilder = new StringBuilder();

                for (Map.Entry<String, List<String>> entry : membersByRole.entrySet()) {
                    stringBuilder.append(entry.getKey()).append(":\n");
                    entry.getValue().forEach(member -> stringBuilder.append(member).append("\n"));
                    stringBuilder.append("\n");
                }

                File file = FileUtils.createTempFile(
                    String.format("export-roles-%s.csv", FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())),
                    stringBuilder.toString()
                );
                event.getHook().sendFiles(FileUpload.fromData(file)).queue();
            } catch (IOException exception) {
                log.error("Failed to create temp file!", exception);
                event.getHook().editOriginal("An error occurred while creating the temp file: " + exception.getMessage()).queue();
            }
        }).onError(throwable -> {
            log.error("Failed to load guild members for role export", throwable);
            event.getHook().editOriginal("Failed to load guild members: " + throwable.getMessage()).queue();
        });
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "member-activity", description = "Export a list of members and their activity", guildOnly = true, requiredPermissions = {"BAN_MEMBERS"})
    public void exportMemberActivity(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "The number of days of inactivity to consider", required = false) int inactivityDays,
        @SlashOption(description = "The number of messages to consider as active", required = false) int inactivityMessages,
        @SlashOption(description = "Role to consider when exporting", required = false) String role
    ) {
        event.deferReply(true).queue();

        if (event.getGuild() == null || event.getMember() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
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
            "Last Mod Mail Activity",
            "Total Recent Messages",
            "Messages Sent (Last " + inactivityDays + "d)",
            "Reviewed",
            "Comments"
        ), ";");

        if (discordUserRepository.isEmpty()) {
            event.getHook().editOriginal("Nothing found to export!").queue();
            return;
        }

        List<DiscordUser> discordUsers = discordUserRepository.getAll();

        if (inactivityDays == -1 && inactivityMessages == -1) {
            log.info(event.getMember().getEffectiveName() + " is exporting member activity for all members");
        } else {
            inactivityDays = inactivityDays != 0 ? inactivityDays : SkyBlockNerdsBot.config().getInactivityDays();
            inactivityMessages = inactivityMessages != 0 ? inactivityMessages : SkyBlockNerdsBot.config().getInactivityMessages();
            long inactivityTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(inactivityDays);

            int finalInactivityDays = inactivityDays;
            int finalInactivityMessages = inactivityMessages;

            discordUsers.removeIf(discordUser -> {
                LastActivity lastActivity = discordUser.getLastActivity();
                return lastActivity.getChannelActivityHistory().stream()
                    .filter(channelActivityEntry -> !Arrays.asList(SkyBlockNerdsBot.config().getChannelConfig().getBlacklistedChannels()).contains(channelActivityEntry.getChannelId()))
                    .anyMatch(entry -> entry.getLastMessageTimestamp() > inactivityTimestamp && discordUser.getLastActivity().getTotalMessageCount(finalInactivityDays) > finalInactivityMessages);
            });

            log.info(event.getMember().getEffectiveName() + " is exporting member activity for " + discordUsers.size() + " members that meet the requirements (" + inactivityDays + " days of inactivity and " + inactivityMessages + " messages)");
        }

        discordUsers.removeIf(discordUser -> {
            Member member = event.getGuild().getMemberById(discordUser.getDiscordId());

            if (member == null) {
                return true;
            }

            Role highestRole = RoleManager.getHighestRole(member);
            if (role != null && highestRole != null) {
                return !highestRole.getName().equalsIgnoreCase(role);
            }

            return RoleManager.hasAnyRole(member, Utils.SPECIAL_ROLES);
        });


        for (DiscordUser discordUser : discordUsers) {
            Member member = event.getGuild().getMemberById(discordUser.getDiscordId());

            if (member == null) {
                log.warn("[Member Activity Export] Member not found for user: " + discordUser.getDiscordId());
                continue;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            List<ChannelActivityEntry> history = new ArrayList<>(lastActivity.getChannelActivityHistory(inactivityDays));
            StringBuilder channelActivity = new StringBuilder();

            if (!history.isEmpty()) {
                history.sort((o1, o2) -> o2.getMessageCount() - o1.getMessageCount());

                for (ChannelActivityEntry entry : history) {
                    channelActivity.append(entry.getLastKnownDisplayName()).append(": ").append(entry.getMessageCount());
                    if (history.indexOf(entry) != history.size() - 1) {
                        channelActivity.append("\n");
                    }
                }
            } else {
                channelActivity.append("N/A");
            }

            csvData.addRow(List.of(
                member.getUser().getName(),
                discordUser.getMojangProfile().getUsername() == null ? "Not Linked" : discordUser.getMojangProfile().getUsername(),
                formatTimestampSheets(lastActivity.getLastGlobalActivity()),
                formatTimestampSheets(lastActivity.getLastVoiceChannelJoinDate()),
                formatTimestampSheets(lastActivity.getLastItemGenUsage()),
                lastActivity.getSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getSuggestionCreationHistory().get(0)),
                lastActivity.getProjectSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getProjectSuggestionCreationHistory().get(0)),
                lastActivity.getAlphaSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getAlphaSuggestionCreationHistory().get(0)),
                lastActivity.getSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getSuggestionVoteHistoryMap())),
                lastActivity.getProjectSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getProjectSuggestionVoteHistoryMap())),
                lastActivity.getAlphaSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getAlphaSuggestionVoteHistoryMap())),
                formatTimestampSheets(lastActivity.getLastProjectActivity()),
                formatTimestampSheets(lastActivity.getLastAlphaActivity()),
                formatTimestampSheets(lastActivity.getLastModMailUsage()),
                String.valueOf(lastActivity.getTotalMessageCount(inactivityDays)),
                "\"" + channelActivity + "\"",
                "FALSE"
            ));

            log.debug("Added member " + member.getUser().getName() + " to the activity export for " + event.getMember().getEffectiveName() + " (days required: " + inactivityDays + ", message count required: " + inactivityMessages + ")");
        }

        try {
            File file = FileUtils.createTempFile(String.format("export-member-activity-%s.csv", FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), csvData.toCSV());
            event.getHook().sendFiles(FileUpload.fromData(file)).queue();
        } catch (IOException exception) {
            log.error("Failed to create temp file!", exception);
            event.getHook().editOriginal("An error occurred while creating the temp file: " + exception.getMessage()).queue();
        }
    }

    private String formatTimestampSheets(long timestamp) {
        if (timestamp < 0) {
            return "N/A";
        }

        return "=EPOCHTODATE(" + timestamp / 1_000 + ")";
    }

    private String formatTimestampLog(long timestamp) {
        if (timestamp < 0) {
            return "N/A";
        }

        return new Date(timestamp) + "/" + timestamp;
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "user-suggestions", description = "Export all suggestions made by a specific user ID", guildOnly = true, requiredPermissions = {"ADMINISTRATOR"})
    public void exportUserSuggestions(SlashCommandInteractionEvent event, @SlashOption(description = "User ID to export suggestions for") String userId) {
        event.deferReply(true).queue();

        try {
            long userIdLong = Long.parseLong(userId);

            List<Suggestion> userSuggestions = DiscordBotEnvironment.getBot().getSuggestionCache()
                .getSuggestions()
                .stream()
                .filter(suggestion -> suggestion.getOwnerIdLong() == userIdLong)
                .filter(Suggestion::notDeleted)
                .toList();

            if (userSuggestions.isEmpty()) {
                event.getHook().editOriginal("No suggestions found for user ID: " + userId).queue();
                return;
            }

            event.getHook().editOriginal(String.format("Found %d suggestions for user ID %s. Starting export...", userSuggestions.size(), userId)).queue();

            CSVData csvData = new CSVData(List.of(
                "Title",
                "Content",
                "Channel Type",
                "Creation Date",
                "Jump URL",
                "Agrees",
                "Disagrees",
                "Neutrals",
                "Ratio",
                "Greenlit",
                "Tags"
            ), ";");

            int totalSuggestions = userSuggestions.size();
            for (int i = 0; i < userSuggestions.size(); i++) {
                Suggestion suggestion = userSuggestions.get(i);

                int progress = i + 1;
                event.getHook().editOriginal(String.format("Processing suggestion %d/%d: \"%s\"",
                    progress, totalSuggestions,
                    suggestion.getThreadName().length() > 50 ?
                        suggestion.getThreadName().substring(0, 50) + "..." :
                        suggestion.getThreadName()
                )).queue();

                String content = suggestion.getFirstMessage()
                    .map(Message::getContentRaw)
                    .orElse("Content not available");

                content = content.replace("\"", "\"\"").replace("\n", " ").replace("\r", "");

                csvData.addRow(List.of(
                    "\"" + suggestion.getThreadName().replace("\"", "\"\"") + "\"",
                    "\"" + content + "\"",
                    suggestion.getChannelType().getName(),
                    formatTimestampSheets(suggestion.getTimeCreated().toInstant().toEpochMilli()),
                    "=HYPERLINK(\"" + suggestion.getJumpUrl() + "\", \"View\")",
                    String.valueOf(suggestion.getAgrees()),
                    String.valueOf(suggestion.getDisagrees()),
                    String.valueOf(suggestion.getNeutrals()),
                    String.format("%.1f%%", suggestion.getRatio()),
                    suggestion.isGreenlit() ? "YES" : "NO",
                    "\"" + suggestion.getAppliedTags().stream()
                        .map(BaseForumTag::getName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("") + "\""
                ));
            }

            event.getHook().editOriginal("Creating export file...").queue();

            FileUtils.createTempFileAsync(
                String.format("export-user-suggestions-%s-%s.csv",
                    userId,
                    FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())),
                csvData.toCSV()
            ).thenAccept(file -> {
                event.getHook().editOriginal(String.format("Successfully exported %d suggestions for user ID %s", userSuggestions.size(), userId))
                    .setFiles(FileUpload.fromData(file))
                    .queue();
            }).exceptionally(throwable -> {
                log.error("Failed to create temp file for user suggestions export", throwable);
                event.getHook().editOriginal("Failed to create temporary file: " + throwable.getMessage()).queue();
                return null;
            });

        } catch (NumberFormatException e) {
            event.getHook().editOriginal("Please provide a valid user ID").queue();
        }
    }
}