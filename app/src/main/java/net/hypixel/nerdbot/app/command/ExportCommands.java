package net.hypixel.nerdbot.app.command;

import com.google.gson.JsonArray;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.ProgressUpdates;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.role.SpecialRoles;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.GreenlitStore;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.suggestion.SuggestionFactory;
import net.hypixel.nerdbot.discord.role.RoleManager;
import net.hypixel.nerdbot.marmalade.csv.CSVData;
import net.hypixel.nerdbot.marmalade.io.FileUtils;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ExportCommands {

    private static final String PARENT_COMMAND = "export";
    static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(10);
    static final String NOTHING_TO_EXPORT = "Nothing found to export!";
    static final String FILE_FAILED = "Failed to create the export file. Please try again later.";

    private final DiscordUserStore users;
    private final GreenlitStore greenlit;
    private final SuggestionCache suggestions;
    private final SuggestionFactory suggestionFactory;
    private final Provider<NerdBotConfig> config;
    private final JDA jda;
    private final Executor backgroundWork;
    private final Clock clock;

    @Inject
    public ExportCommands(DiscordUserStore users, GreenlitStore greenlit, SuggestionCache suggestions, SuggestionFactory suggestionFactory,
                          Provider<NerdBotConfig> config, JDA jda, Executor backgroundWork, Clock clock) {
        this.users = users;
        this.greenlit = greenlit;
        this.suggestions = suggestions;
        this.suggestionFactory = suggestionFactory;
        this.config = config;
        this.jda = jda;
        this.backgroundWork = backgroundWork;
        this.clock = clock;
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "threads", description = "Export threads from a Forum Channel", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void exportForumThreads(SlashCommandInteractionEvent event, @SlashOption ForumChannel forumChannel) {
        exportThreads(event, forumChannel);
    }

    void exportThreads(IReplyCallback interaction, ForumChannel forumChannel) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        hook.editOriginal(String.format("Exporting threads from %s...", forumChannel.getAsMention())).queue();

        ProgressUpdates progress = ProgressUpdates.throttledReply(hook, PROGRESS_INTERVAL, clock);
        CompletableFuture<File> export = CompletableFuture.supplyAsync(() -> {
            try {
                return ArchiveExporter.exportForumThreadsDetailed(forumChannel, progress.loggingReporter());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, backgroundWork);

        CommandReplies.finish(export, hook, "export threads from " + forumChannel.getId(), FILE_FAILED, file -> progress.finish(() ->
            hook.editOriginal(String.format("Finished exporting all threads from %s!", forumChannel.getAsMention()))
                .setFiles(FileUpload.fromData(file))
                .queue()));
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "greenlit", description = "Exports all greenlit forum posts into a CSV file", guildOnly = true, defaultMemberPermissions = {"MANAGE_CHANNEL", "MANAGE_THREADS"}, requiredPermissions = {"MANAGE_CHANNEL", "MANAGE_THREADS"})
    public void exportGreenlitThreads(SlashCommandInteractionEvent event, @SlashOption(description = "Disregards any post before this UNIX timestamp (Default: 0)", required = false) long suggestionsAfter) {
        exportGreenlit(event, suggestionsAfter);
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "uuids", description = "Get all assigned Minecraft Names/UUIDs from all specified roles (requires Member) in the server.", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void userList(SlashCommandInteractionEvent event, @SlashOption(description = "Comma-separated role names to search for (Default: Member)", required = false) String roles) {
        exportUuids(event, roles);
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "roles", description = "Export a list of users with the given roles", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void exportRoles(SlashCommandInteractionEvent event, @SlashOption(description = "Comma-separated list of role names (e.g. Role 1, Role 2, Role 3)") String roles) {
        exportRoleMembers(event, roles);
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "member-activity", description = "Export a list of members and their activity", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void exportMemberActivity(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "The number of days of inactivity to consider", required = false) int inactivityDays,
        @SlashOption(description = "The number of messages to consider as active", required = false) int inactivityMessages,
        @SlashOption(description = "Role to consider when exporting", required = false) String role
    ) {
        exportActivity(event, inactivityDays, inactivityMessages, role);
    }

    @SlashCommand(name = PARENT_COMMAND, subcommand = "user-suggestions", description = "Export all suggestions made by a specific user ID", guildOnly = true, defaultMemberPermissions = {"ADMINISTRATOR"}, requiredPermissions = {"ADMINISTRATOR"})
    public void exportUserSuggestions(SlashCommandInteractionEvent event, @SlashOption(description = "User ID to export suggestions for") String userId) {
        exportSuggestionsBy(event, userId);
    }

    void exportUuids(IReplyCallback interaction, @Nullable String roles) {
        String[] roleNames = roles != null ? roles.split(", ?") : new String[]{"Member"};
        exportFromMembers(interaction, "export Minecraft UUIDs", (members, stored) -> uuidJson(members, stored, roleNames)
            .map(json -> namedFile("export-uuids-%s.json", json)));
    }

    void exportRoleMembers(IReplyCallback interaction, String roles) {
        String[] roleNames = roles.split(", ?");
        exportFromMembers(interaction, "export role members", (members, stored) -> rolesText(members, roleNames)
            .map(text -> namedFile("export-roles-%s.csv", text)));
    }

    void exportGreenlit(IReplyCallback interaction, long suggestionsAfter) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        CommandReplies.finish(greenlit.loadAllSaved(), hook, "export greenlit suggestions", FILE_FAILED, messages -> {
            Optional<String> csv = greenlitCsv(messages, suggestionsAfter);
            log.info("Exporting greenlit suggestions created after {} ({} stored)", suggestionsAfter, messages.size());
            if (csv.isEmpty()) {
                hook.editOriginal("No suggestions were greenlit").queue();
                return;
            }

            MessageEditData data = MessageEditBuilder.from(MessageEditData.fromContent("To import into Google Sheets, go to File -> Import, Upload the `.csv` document shown below.\nChange `Import Location` to `Append to current sheet` and `Separator Type` should be defaulted to Automatic detection if not, change it to tabs.)"))
                .setFiles(FileUpload.fromData(namedFile("export-greenlit-%s.csv", csv.get())))
                .build();
            hook.editOriginal(data).queue();
        });
    }

    void exportActivity(IReplyCallback interaction, int inactivityDays, int inactivityMessages, @Nullable String role) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        Guild guild = interaction.getGuild();
        if (guild == null) {
            hook.editOriginal("This command can only be used in a server!").queue();
            return;
        }

        NerdBotConfig current = config.get();
        ActivityFilter filter = ActivityFilter.from(inactivityDays, inactivityMessages, role, current);

        // The user cache drops members after a day without activity, which is exactly who this export looks for
        CompletableFuture<List<DiscordUser>> stored = CompletableFuture.supplyAsync(users::getAllFromDatabase, backgroundWork);
        CommandReplies.finish(stored, hook, "export member activity", FILE_FAILED, all -> {
            List<DiscordUser> selected = selectForActivityExport(all, guild::getMemberById, filter, current, clock.millis());
            if (selected.isEmpty()) {
                hook.editOriginal(NOTHING_TO_EXPORT).queue();
                return;
            }

            CSVData csvData = new CSVData(memberActivityHeaders(filter.days()), ";");
            for (DiscordUser user : selected) {
                Member member = guild.getMemberById(user.getDiscordId());
                if (member != null) {
                    csvData.addRow(memberActivityRow(member.getUser().getName(), user, filter.days()));
                }
            }

            log.info("Exporting member activity for {} members ({})", selected.size(), filter);
            hook.sendFiles(FileUpload.fromData(namedFile("export-member-activity-%s.csv", csvData.toCSV()))).queue();
        });
    }

    void exportSuggestionsBy(IReplyCallback interaction, String userId) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        long ownerId;
        try {
            ownerId = Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            hook.editOriginal("Please provide a valid user ID").queue();
            return;
        }

        List<Suggestion> owned = suggestions.all().stream()
            .filter(suggestion -> suggestion.getOwnerIdLong() == ownerId)
            .filter(Suggestion::notDeleted)
            .toList();
        if (owned.isEmpty()) {
            hook.editOriginal("No suggestions found for user ID: " + userId).queue();
            return;
        }

        hook.editOriginal(String.format("Found %d suggestions for user ID %s. Starting export...", owned.size(), userId)).queue();
        ProgressUpdates progress = ProgressUpdates.throttledReply(hook, PROGRESS_INTERVAL, clock);
        AtomicInteger done = new AtomicInteger();

        List<CompletableFuture<List<String>>> rows = owned.stream()
            .map(suggestion -> firstMessageContent(suggestion).thenApply(content -> {
                progress.report(String.format("Processed %d/%d suggestions", done.incrementAndGet(), owned.size()));
                return userSuggestionRow(suggestion, content, suggestion.getAppliedTags(jda).stream().map(BaseForumTag::getName).toList());
            }))
            .toList();

        CompletableFuture<File> file = CompletableFuture.allOf(rows.toArray(CompletableFuture[]::new)).thenApplyAsync(ignored -> {
            CSVData csvData = new CSVData(USER_SUGGESTION_HEADERS, ";");
            rows.forEach(row -> csvData.addRow(row.join()));
            return namedFile("export-user-suggestions-" + ownerId + "-%s.csv", csvData.toCSV());
        }, backgroundWork);

        CommandReplies.finish(file, hook, "export suggestions by " + ownerId, FILE_FAILED, exported -> progress.finish(() ->
            hook.editOriginal(String.format("Successfully exported %d suggestions for user ID %s", owned.size(), userId))
                .setFiles(FileUpload.fromData(exported))
                .queue()));
    }

    /** Loads every member, reads every stored user once, and sends the file the builder makes, if any. */
    private void exportFromMembers(IReplyCallback interaction, String action, MemberExport builder) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        Guild guild = interaction.getGuild();
        if (guild == null) {
            hook.editOriginal("This command can only be used in a server!").queue();
            return;
        }

        CompletableFuture<List<Member>> members = new CompletableFuture<>();
        guild.loadMembers().onSuccess(members::complete).onError(members::completeExceptionally);

        CompletableFuture<Optional<File>> file = members.thenApplyAsync(loaded -> builder.build(loaded, users.getAllFromDatabase()), backgroundWork);
        CommandReplies.finish(file, hook, action, FILE_FAILED, made -> made.ifPresentOrElse(
            exported -> hook.sendFiles(FileUpload.fromData(exported)).queue(),
            () -> hook.editOriginal(NOTHING_TO_EXPORT).queue()));
    }

    @FunctionalInterface
    private interface MemberExport {
        Optional<File> build(List<Member> members, List<DiscordUser> storedUsers);
    }

    private CompletableFuture<String> firstMessageContent(Suggestion suggestion) {
        ThreadChannel thread = jda.getThreadChannelById(suggestion.getThreadId());
        if (thread == null) {
            return CompletableFuture.completedFuture("Content not available");
        }
        return suggestionFactory.firstMessage(thread)
            .thenApply(message -> message.map(Message::getContentRaw).orElse("Content not available"))
            .exceptionally(error -> {
                log.warn("Couldn't fetch the first message of suggestion {}", suggestion.getThreadId(), error);
                return "Content not available";
            });
    }

    /**
     * Which members the activity export lists.
     *
     * @param everyone true for -1/-1, which lists every member regardless of activity
     */
    record ActivityFilter(int days, int messages, @Nullable String role, boolean everyone) {

        /** 0 means the config's default; -1 for both days and messages means everyone. */
        static ActivityFilter from(int days, int messages, @Nullable String role, NerdBotConfig config) {
            boolean everyone = days == -1 && messages == -1;
            int effectiveDays = days > 0 ? days : config.getInactivityDays();
            int effectiveMessages = messages > 0 ? messages : config.getInactivityMessages();
            return new ActivityFilter(effectiveDays, effectiveMessages, role, everyone);
        }
    }

    /**
     * The users to list: still in the server, without a staff role (or with the given highest role),
     * and, unless exporting everyone, not active enough in non-blacklisted channels.
     */
    static List<DiscordUser> selectForActivityExport(List<DiscordUser> all, Function<String, Member> memberLookup, ActivityFilter filter,
                                                     NerdBotConfig config, long nowMillis) {
        String[] blacklistedChannels = config.getChannelConfig().getBlacklistedChannels();
        Set<String> blacklisted = blacklistedChannels == null ? Set.of() : Set.of(blacklistedChannels);
        long activeSince = nowMillis - TimeUnit.DAYS.toMillis(filter.days());

        return all.stream()
            .filter(user -> filter.everyone() || !isActive(user, blacklisted, activeSince, filter))
            .filter(user -> {
                Member member = memberLookup.apply(user.getDiscordId());
                if (member == null) {
                    return false;
                }

                Role highestRole = RoleManager.getHighestRole(member);
                if (filter.role() != null && highestRole != null) {
                    return highestRole.getName().equalsIgnoreCase(filter.role());
                }
                return !RoleManager.hasAnyRole(member, SpecialRoles.NAMES);
            })
            .toList();
    }

    private static boolean isActive(DiscordUser user, Set<String> blacklisted, long activeSince, ActivityFilter filter) {
        LastActivity activity = user.getLastActivity();
        return activity.getChannelActivityHistory().stream()
            .filter(entry -> !blacklisted.contains(entry.getChannelId()))
            .anyMatch(entry -> entry.getLastMessageTimestamp() > activeSince && activity.getTotalMessageCount(filter.days()) > filter.messages());
    }

    /** The UUIDs of linked, non-bot members with any of the roles, or empty when there are none. */
    static Optional<String> uuidJson(List<Member> members, List<DiscordUser> storedUsers, String... roleNames) {
        Map<String, DiscordUser> usersById = storedUsers.stream()
            .collect(Collectors.toMap(DiscordUser::getDiscordId, user -> user, (first, second) -> first));

        List<MojangProfile> profiles = members.stream()
            .filter(member -> !member.getUser().isBot())
            .filter(member -> RoleManager.hasAnyRole(member, roleNames))
            .map(member -> usersById.get(member.getId()))
            .filter(user -> user != null && user.isProfileAssigned())
            .map(DiscordUser::getMojangProfile)
            .toList();

        if (profiles.isEmpty()) {
            return Optional.empty();
        }

        JsonArray uuids = new JsonArray();
        profiles.forEach(profile -> uuids.add(profile.getUniqueId().toString()));
        return Optional.of(DataSerialization.GSON.toJson(uuids));
    }

    /** Each requested role, in the order asked for, followed by its members; empty when nobody has any of them. */
    static Optional<String> rolesText(List<Member> members, String... roleNames) {
        Map<String, List<String>> membersByRole = new LinkedHashMap<>();
        for (String roleName : roleNames) {
            List<String> names = members.stream()
                .filter(member -> member.getRoles().stream().anyMatch(role -> role.getName().equalsIgnoreCase(roleName)))
                .map(Member::getEffectiveName)
                .toList();
            if (!names.isEmpty()) {
                membersByRole.put(roleName, names);
            }
        }

        if (membersByRole.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder();
        membersByRole.forEach((roleName, names) -> {
            text.append(roleName).append(":\n");
            names.forEach(name -> text.append(name).append("\n"));
            text.append("\n");
        });
        return Optional.of(text.toString());
    }

    /** The greenlit suggestions from {@code suggestionsAfter} on, as CSV for Google Sheets; empty when there are none. */
    static Optional<String> greenlitCsv(List<GreenlitMessage> messages, long suggestionsAfter) {
        CSVData csvData = new CSVData(List.of("Creation Date", "Tags", "Title"), ";");

        for (GreenlitMessage message : messages) {
            if (message.getSuggestionTimestamp() < suggestionsAfter) {
                continue;
            }

            csvData.addRow(List.of(
                formatTimestampSheets(message.getSuggestionTimestamp()),
                quoted(String.join(", ", message.getTags())),
                "=HYPERLINK(\"" + message.getSuggestionUrl() + "\", \"" + message.getSuggestionTitle().replace("\"", "\"\"") + "\")"
            ));
        }

        return csvData.hasContent() ? Optional.of(csvData.toCSV()) : Optional.empty();
    }

    static final List<String> USER_SUGGESTION_HEADERS = List.of(
        "Title", "Content", "Channel Type", "Creation Date", "Jump URL", "Agrees", "Disagrees", "Neutrals", "Ratio", "Greenlit", "Tags");

    static List<String> userSuggestionRow(Suggestion suggestion, String content, List<String> tags) {
        return List.of(
            quoted(suggestion.getThreadName()),
            quoted(content.replace("\n", " ").replace("\r", "")),
            suggestion.getChannelType().getName(),
            formatTimestampSheets(suggestion.getTimeCreated().toInstant().toEpochMilli()),
            "=HYPERLINK(\"" + suggestion.getJumpUrl() + "\", \"View\")",
            String.valueOf(suggestion.getAgrees()),
            String.valueOf(suggestion.getDisagrees()),
            String.valueOf(suggestion.getNeutrals()),
            String.format("%.1f%%", suggestion.getRatio()),
            suggestion.isGreenlit() ? "YES" : "NO",
            quoted(String.join(", ", tags))
        );
    }

    static List<String> memberActivityHeaders(int inactivityDays) {
        return List.of(
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
        );
    }

    static List<String> memberActivityRow(String discordUsername, DiscordUser discordUser, int inactivityDays) {
        LastActivity lastActivity = discordUser.getLastActivity();
        List<ChannelActivityEntry> history = new ArrayList<>(lastActivity.getChannelActivityHistory(inactivityDays));
        history.sort((o1, o2) -> o2.getMessageCount() - o1.getMessageCount());

        String channelActivity = history.isEmpty()
            ? "N/A"
            : history.stream()
                .map(entry -> entry.getLastKnownDisplayName() + ": " + entry.getMessageCount())
                .collect(Collectors.joining("\n"));

        return List.of(
            discordUsername,
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
            quoted(channelActivity),
            "FALSE",
            ""
        );
    }

    /** A CSV field in quotes, with any quotes inside doubled. */
    static String quoted(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String formatTimestampSheets(long timestamp) {
        if (timestamp < 0) {
            return "N/A";
        }

        return "=EPOCHTODATE(" + timestamp / 1_000 + ")";
    }

    /** Writes a temp file whose name has the current time in place of {@code %s}. */
    private static File namedFile(String nameFormat, String content) {
        try {
            return FileUtils.createTempFile(String.format(nameFormat, FileUtils.FILE_NAME_DATE_FORMAT.format(Instant.now())), content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
