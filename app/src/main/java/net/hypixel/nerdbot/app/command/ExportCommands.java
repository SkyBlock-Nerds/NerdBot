package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.csv.CSVData;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.marmalade.storage.database.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ExportCommands {

    private static final String PARENT_COMMAND = "export";

    @SlashCommand(name = PARENT_COMMAND, subcommand = "threads", description = "Export threads from a Forum Channel", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void exportForumThreads(SlashCommandInteractionEvent event, @SlashOption ForumChannel forumChannel) {
        event.deferReply(true).queue();

        if (event.getMember() == null) {
            event.getHook().editOriginal("This command can only be used in a server!").queue();
            return;
        }

        event.getHook().editOriginal("Exporting threads from " + forumChannel.getAsMention() + "...").queue();

        CompletableFuture.runAsync(() -> {
            try {
                File file = ArchiveExporter.exportForumThreadsDetailed(forumChannel, status -> event.getHook().editOriginal(status).queue());
                event.getHook().editOriginal("Finished exporting all threads from " + forumChannel.getAsMention() + "!")
                    .setFiles(FileUpload.fromData(file))
                    .queue();
            } catch (IOException exception) {
                log.error("Failed to create temp file!", exception);
                event.getHook().editOriginal("Failed to create temporary file: " + exception.getMessage()).queue();
            } catch (Exception exception) {
                log.error("Failed to export threads!", exception);
                event.getHook().editOriginal("Failed to export threads: " + exception.getMessage()).queue();
            }
        });
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "greenlit", description = "Exports all greenlit forum posts into a CSV file", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL", "MANAGE_THREADS"}, requiredPermissions = {"MANAGE_CHANNEL", "MANAGE_THREADS"})
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
            log.info("No greenlit suggestions found for user '{}' (ID: {}) export (after: {}, current time: {})", event.getMember().getEffectiveName(), event.getMember().getId(), formatTimestampLog(suggestionsAfter), formatTimestampLog(System.currentTimeMillis()));
            event.getHook().editOriginal("No suggestions were greenlit").queue();
            return;
        }

        // Send a log line with formatted dates for the suggestionsAfter parameter
        log.info("Exporting {} greenlit suggestions for user '{}' (ID: {}) (after: {}, current time: {})", output.size(), event.getMember().getEffectiveName(), event.getMember().getId(), formatTimestampLog(suggestionsAfter), formatTimestampLog(System.currentTimeMillis()));

        CSVData csvData = new CSVData(List.of("Creation Date", "Tags", "Title"), ";");

        for (GreenlitMessage greenlitMessage : output) {
            // If we manually limited the timestamps to before "x" time (defaults to 0) it "removes" the greenlit suggestions from appearing in the linked CSV file.
            if (greenlitMessage.getSuggestionTimestamp() >= suggestionsAfter) {
                csvData.addRow(List.of(
                    formatTimestampSheets(greenlitMessage.getSuggestionTimestamp()),
                    "\"" + String.join(", ", greenlitMessage.getTags()) + "\"",
                    "=HYPERLINK(\"" + greenlitMessage.getSuggestionUrl() + "\", \"" + greenlitMessage.getSuggestionTitle().replace("\"", "\"\"") + "\")"
                ));
                log.info("Added greenlit suggestion '{}' to the greenlit suggestion export for user '{}' (ID: {}) (after: {}, current time: {})", greenlitMessage.getSuggestionTitle(), event.getMember().getEffectiveName(), event.getMember().getId(), formatTimestampLog(suggestionsAfter), formatTimestampLog(System.currentTimeMillis()));
            } else {
                log.debug("Skipping greenlit suggestion '{}' because it was created before the specified timestamp (after: {}, suggestion timestamp: {})", greenlitMessage.getSuggestionTitle(), formatTimestampLog(suggestionsAfter), formatTimestampLog(greenlitMessage.getSuggestionTimestamp()));
            }
        }

        if (!csvData.hasContent()) {
            log.info("No greenlit suggestions found for user '{}' (ID: {}) export (after: {})", event.getMember().getEffectiveName(), event.getMember().getId(), formatTimestampLog(suggestionsAfter));
            event.getHook().editOriginal("No suggestions were greenlit").queue();
            return;
        }

        try {
            MessageEditData data = MessageEditBuilder.from(MessageEditData.fromContent("To import into Google Sheets, go to File -> Import, Upload the `.csv` document shown below.\nChange `Import Location` to `Append to current sheet` and `Separator Type` should be defaulted to Automatic detection if not, change it to tabs.)"))
                .setFiles(FileUpload.fromData(FileUtils.createTempFile("export-greenlit-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".csv", csvData.toCSV())))
                .build();

            event.getHook().editOriginal(data).queue();
        } catch (IOException exception) {
            event.getHook().editOriginal("Failed to create temporary file: " + exception.getMessage()).queue();
            log.error("Failed to create temp file!", exception);
        }
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "uuids", description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
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
                .map(Member::getId)
                .map(discordUserRepository::findById)
                .filter(Objects::nonNull)
                .filter(DiscordUser::isProfileAssigned)
                .map(DiscordUser::getMojangProfile)
                .toList();

            log.info("Found {} members meeting requirements", profiles.size());

            if (profiles.isEmpty()) {
                event.getHook().editOriginal("Nothing found to export!").queue();
                return;
            }

            JsonArray uuidArray = new JsonArray();
            profiles.forEach(profile -> uuidArray.add(profile.getUniqueId().toString()));

            try {
                File file = FileUtils.createTempFile(
                    "export-uuids-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".json",
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

    @SlashCommand(name = PARENT_COMMAND, subcommand = "roles", description = "Export a list of users with the given roles", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
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

                membersByRole.forEach((role, names) -> {
                    stringBuilder.append(role).append(":\n");
                    names.forEach(name -> stringBuilder.append(name).append("\n"));
                    stringBuilder.append("\n");
                });

                File file = FileUtils.createTempFile(
                    "export-roles-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".csv",
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

    @SlashCommand(name = PARENT_COMMAND, subcommand = "member-activity", description = "Export a list of members and their activity", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
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
            "Last Ticket Activity",
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
            log.info("User '{}' (ID: {}) is exporting member activity for all members", event.getMember().getEffectiveName(), event.getMember().getId());
        } else {
            inactivityDays = inactivityDays != 0 ? inactivityDays : SkyBlockNerdsBot.config().getInactivityDays();
            inactivityMessages = inactivityMessages != 0 ? inactivityMessages : SkyBlockNerdsBot.config().getInactivityMessages();
            long inactivityTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(inactivityDays);

            int finalInactivityDays = inactivityDays;
            int finalInactivityMessages = inactivityMessages;

            discordUsers.removeIf(discordUser -> {
                LastActivity lastActivity = discordUser.getLastActivity();
                return lastActivity.getChannelActivityHistory().stream()
                    .filter(channelActivityEntry -> Arrays.stream(SkyBlockNerdsBot.config().getChannelConfig().getBlacklistedChannels()).noneMatch(channelActivityEntry.getChannelId()::equals))
                    .anyMatch(entry -> entry.getLastMessageTimestamp() > inactivityTimestamp && discordUser.getLastActivity().getTotalMessageCount(finalInactivityDays) > finalInactivityMessages);
            });

            log.info("User '{}' (ID: {}) is exporting member activity for {} members that meet the requirements ({} days of inactivity and {} messages)", event.getMember().getEffectiveName(), event.getMember().getId(), discordUsers.size(), inactivityDays, inactivityMessages);
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
                log.warn("[Member Activity Export] Member not found for user ID: {}", discordUser.getDiscordId());
                continue;
            }

            LastActivity lastActivity = discordUser.getLastActivity();
            List<ChannelActivityEntry> history = new ArrayList<>(lastActivity.getChannelActivityHistory(inactivityDays));
            StringBuilder channelActivity = new StringBuilder();

            if (!history.isEmpty()) {
                history.sort(Comparator.comparingInt(ChannelActivityEntry::getMessageCount).reversed());

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
                lastActivity.getSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getSuggestionCreationHistory().getFirst()),
                lastActivity.getProjectSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getProjectSuggestionCreationHistory().getFirst()),
                lastActivity.getAlphaSuggestionCreationHistory().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getAlphaSuggestionCreationHistory().getFirst()),
                lastActivity.getSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getSuggestionVoteHistoryMap())),
                lastActivity.getProjectSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getProjectSuggestionVoteHistoryMap())),
                lastActivity.getAlphaSuggestionVoteHistoryMap().isEmpty() ? "N/A" : formatTimestampSheets(lastActivity.getNewestEntry(lastActivity.getAlphaSuggestionVoteHistoryMap())),
                formatTimestampSheets(lastActivity.getLastProjectActivity()),
                formatTimestampSheets(lastActivity.getLastAlphaActivity()),
                formatTimestampSheets(lastActivity.getLastTicketUsage()),
                String.valueOf(lastActivity.getTotalMessageCount(inactivityDays)),
                "\"" + channelActivity + "\"",
                "FALSE"
            ));

            log.debug("Added member '{}' (ID: {}) to the activity export for user '{}' (ID: {}) (days required: {}, message count required: {})", member.getUser().getName(), member.getId(), event.getMember().getEffectiveName(), event.getMember().getId(), inactivityDays, inactivityMessages);
        }

        try {
            File file = FileUtils.createTempFile("export-member-activity-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".csv", csvData.toCSV());
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

    @SlashCommand(name = PARENT_COMMAND, subcommand = "user-suggestions", description = "Export all suggestions made by a specific user ID", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
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

            event.getHook().editOriginal("Found " + userSuggestions.size() + " suggestions for user ID " + userId + ". Starting export...").queue();

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
                    StringUtils.truncate(suggestion.getThreadName(), 53)
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
                        .collect(Collectors.joining(", ")) + "\""
                ));
            }

            event.getHook().editOriginal("Creating export file...").queue();

            FileUtils.createTempFileAsync(
                "export-user-suggestions-" + userId + "-" + FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now()) + ".csv",
                csvData.toCSV()
            ).thenAccept(file -> {
                event.getHook().editOriginal("Successfully exported " + userSuggestions.size() + " suggestions for user ID " + userId)
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
