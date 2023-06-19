package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.Function;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.reminder.Reminder;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
public class UserCommands extends ApplicationCommand {

    private static final List<String> GREENLIT_TAGS = Arrays.asList("greenlit", "docced");

    @JDASlashCommand(name = "remind", subcommand = "create", description = "Set a reminder")
    public void createReminder(GuildSlashEvent event, @AppOption(description = "Use a format such as \"in 1 hour\" or \"1w3d7h\"") String time, @AppOption String description, @AppOption(description = "Send the reminder through DMs") @Optional Boolean silent) {
        // Check if the bot has permission to send messages in the channel
        if (!event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
            event.reply("I don't have permission to send messages in this channel!").setEphemeral(true).queue();
            return;
        }

        if (event.getChannel() instanceof ThreadChannel && !event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND_IN_THREADS)) {
            event.reply("I don't have permission to send messages in threads!").setEphemeral(true).queue();
            return;
        }

        Date date = null;
        boolean parsed = false;

        // Try parse the time using the Natty dependency
        try {
            Parser parser = new Parser();
            List<DateGroup> groups = parser.parse(time);
            DateGroup group = groups.get(0);

            List<Date> dates = group.getDates();
            date = dates.get(0);
            parsed = true;
        } catch (IndexOutOfBoundsException ignored) {
            // Ignore this exception, it means that the provided time was not parsed
        }

        // Try parse the time using the regex if the above failed
        if (!parsed) {
            try {
                date = parseCustomFormat(time);
            } catch (DateTimeParseException exception) {
                event.reply("Could not parse the provided time!").setEphemeral(true).queue();
                return;
            }
        }

        // Check if the provided time is in the past
        if (date.before(new Date())) {
            event.reply("You can't set a reminder in the past!").setEphemeral(true).queue();
            return;
        }

        if (silent == null) {
            silent = false;
        }

        // Create a new reminder and save it to the database
        Reminder reminder = new Reminder(description, date, event.getChannel().getId(), event.getUser().getId(), silent);
        InsertOneResult result = reminder.save();

        // Check if the reminder was saved successfully, schedule it and send a confirmation message
        if (result != null && result.wasAcknowledged() && result.getInsertedId() != null) {
            event.reply("I will remind you at " + new DiscordTimestamp(date.getTime()).toLongDateTime() + " about:")
                    .addEmbeds(new EmbedBuilder().setDescription(description).build())
                    .setEphemeral(true)
                    .queue();
            reminder.schedule();
        } else {
            // If the reminder could not be saved, send an error message and log the error too
            event.reply("Could not save your reminder, please try again later!").queue();
            log.error("Could not save reminder: " + reminder + " for user: " + event.getUser().getId() + " (" + result + ")");
        }
    }

    @JDASlashCommand(name = "remind", subcommand = "list", description = "View your reminders")
    public void listReminders(GuildSlashEvent event) {
        List<Reminder> reminders = NerdBotApp.getBot().getDatabase().findDocument(NerdBotApp.getBot().getDatabase().getCollection("reminders", Reminder.class), Filters.eq("userId", event.getUser().getId())).into(new ArrayList<>());

        if (reminders.isEmpty()) {
            event.reply("You have no reminders!").setEphemeral(true).queue();
            return;
        }

        // Sort these by newest first
        reminders.sort((o1, o2) -> {
            if (o1.getTime().before(o2.getTime())) {
                return -1;
            } else if (o1.getTime().after(o2.getTime())) {
                return 1;
            } else {
                return 0;
            }
        });

        StringBuilder builder = new StringBuilder("**Your reminders:**\n");
        for (Reminder reminder : reminders) {
            builder.append("(").append(reminder.getUuid()).append(") ").append(new DiscordTimestamp(reminder.getTime().getTime()).toLongDateTime()).append(" - `").append(reminder.getDescription()).append("`\n");
        }

        builder.append("\n**You can delete reminders with `/remind delete <uuid>`**");
        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "remind", subcommand = "delete", description = "Delete a reminder")
    public void deleteReminder(GuildSlashEvent event, @AppOption String uuid) {
        try {
            UUID parsed = UUID.fromString(uuid);
            Reminder reminder = NerdBotApp.getBot().getDatabase().findDocument(NerdBotApp.getBot().getDatabase().getCollection("reminders", Reminder.class), Filters.and(Filters.eq("userId", event.getUser().getId()), Filters.eq("uuid", parsed))).first();

            // Check if the reminder exists first
            if (reminder == null) {
                event.reply("Could not find reminder: " + uuid).setEphemeral(true).queue();
                return;
            }

            DeleteResult result = reminder.delete();

            // Check if the reminder was deleted successfully, cancel the timer and send a confirmation message
            if (result != null && result.wasAcknowledged()) {
                if (reminder.getTimer() != null) {
                    reminder.getTimer().cancel();
                }

                event.reply("Deleted reminder: " + uuid + " (" + reminder.getDescription() + ")").setEphemeral(true).queue();
                log.info("Deleted reminder: " + uuid + " for user: " + event.getUser().getId());
            } else {
                // If the reminder could not be deleted, send an error message and log the error
                event.reply("Could not delete reminder: " + uuid).setEphemeral(true).queue();
                log.info("Could not delete reminder " + uuid + " for user: " + event.getUser().getId() + " (" + result + ")");
            }
        } catch (IllegalArgumentException exception) {
            event.reply("Please enter a valid UUID!").setEphemeral(true).queue();
        }
    }

    /**
     * Parse a time string in the format of {@code 1w2d3h4m5s} into a Date
     *
     * @param time The time string to parse
     * @return The parsed string as a Date
     * @throws DateTimeParseException If the string could not be parsed
     */
    public static Date parseCustomFormat(String time) throws DateTimeParseException {
        Pattern pattern = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");
        Matcher matcher = pattern.matcher(time);

        if (!matcher.matches()) {
            throw new DateTimeParseException("Could not parse date: " + time, time, 0);
        }

        Duration duration = Duration.ZERO;
        if (matcher.group(2) != null) {
            duration = duration.plusDays(Long.parseLong(matcher.group(2)) * 7);
        }

        if (matcher.group(4) != null) {
            duration = duration.plusDays(Long.parseLong(matcher.group(4)));
        }

        if (matcher.group(6) != null) {
            duration = duration.plusHours(Long.parseLong(matcher.group(6)));
        }

        if (matcher.group(8) != null) {
            duration = duration.plusMinutes(Long.parseLong(matcher.group(8)));
        }

        if (matcher.group(10) != null) {
            duration = duration.plusSeconds(Long.parseLong(matcher.group(10)));
        }

        Instant date = Instant.now().plus(duration);
        return Date.from(date);
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-member", description = "View user suggestions.")
    public void viewOwnSuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Member to view.") @Optional Member member,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        BotConfig config = NerdBotApp.getBot().getConfig();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final Member searchMember = (member == null) ? event.getMember() : member;
        final boolean isAlpha = (alpha != null && alpha);
        final String[] suggestionForumIds = (alpha != null && alpha) ? config.getAlphaSuggestionForumIds() : config.getSuggestionForumIds();

        if (suggestionForumIds == null || suggestionForumIds.length == 0) {
            event.reply("No " + (isAlpha ? "alpha " : "") + "suggestion forums are setup in the config.").setEphemeral(true).queue();
            return;
        }

        List<Suggestion> suggestions = getSuggestions(suggestionForumIds, searchMember, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.reply("Found no suggestions matching the specified filters!").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum).setAuthor(searchMember.getEffectiveName()).build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "suggestions", subcommand = "by-everyone", description = "View all suggestions.")
    public void viewAllSuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Toggle alpha suggestions.") @Optional Boolean alpha
    ) {
        BotConfig config = NerdBotApp.getBot().getConfig();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        final boolean isAlpha = (alpha != null && alpha);
        final String[] suggestionForumIds = isAlpha ? config.getAlphaSuggestionForumIds() : config.getSuggestionForumIds();

        if (suggestionForumIds == null || suggestionForumIds.length == 0) {
            event.reply("No " + (isAlpha ? "alpha " : "") + "suggestion forums are setup in the config.").setEphemeral(true).queue();
            return;
        }

        List<Suggestion> suggestions = getSuggestions(suggestionForumIds, null, tags, title, isAlpha);

        if (suggestions.isEmpty()) {
            event.reply("You have no suggestions matched with tags " + (tags != null ? ("`" + tags + "`") : "*ANY*") + " or title containing " + (title != null ? ("`" + title + "`") : "*ANYTHING*") + ".").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildSuggestionsEmbed(suggestions, tags, title, isAlpha, pageNum).build()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "activity", description = "View your recent activity.")
    public void viewOwnActivity(GuildSlashEvent event) {
        Pair<EmbedBuilder, EmbedBuilder> activityEmbeds = getActivityEmbeds(event.getMember());

        if (activityEmbeds.getLeft() == null || activityEmbeds.getRight() == null) {
            event.reply("Couldn't find that user in the database!").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(activityEmbeds.getLeft().build(), activityEmbeds.getRight().build())
            .setEphemeral(true)
            .queue();
    }

    private static List<Suggestion> getSuggestions(String[] suggestionForumIds, Member member, String tags, String title, boolean alpha) {
        final List<String> searchTags = Arrays.asList(tags != null ? tags.split(", *") : new String[0]);

        return Arrays.stream(suggestionForumIds)
            .filter(Objects::nonNull)
            .map(forumId -> NerdBotApp.getBot().getJDA().getForumChannelById(forumId))
            .filter(Objects::nonNull)
            .flatMap(forumChannel -> forumChannel.getThreadChannels()
                .stream()
                .sorted((o1, o2) -> Long.compare(o2.getTimeCreated().toInstant().toEpochMilli(), o1.getTimeCreated().toInstant().toEpochMilli())) // Sort by most recent
                .filter(thread -> member == null || thread.getOwnerIdLong() == member.getIdLong())
                .filter(thread -> thread.getHistoryFromBeginning(1).complete().getRetrievedHistory().get(0) != null) // Lol
                .filter(thread -> searchTags.isEmpty() || searchTags.stream().allMatch(tag -> thread.getAppliedTags()
                    .stream()
                    .anyMatch(forumTag -> forumTag.getName().equalsIgnoreCase(tag))
                ))
                .filter(thread -> title == null || thread.getName()
                    .toLowerCase()
                    .contains(title.toLowerCase())
                )
                .map(thread -> new Suggestion(thread, alpha))
            )
            .toList();
    }

    private static EmbedBuilder buildSuggestionsEmbed(List<Suggestion> suggestions, String tags, String title, boolean alpha, int pageNum) {
        EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();
        List<Suggestion> pages = InfoCommands.getPage(suggestions, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);
        List<List<String>> fieldData = new ArrayList<>();
        double total = suggestions.size();
        double greenlit = suggestions.stream().filter(Suggestion::isGreenlit).count();

        for (Suggestion suggestion : pages) {
            String name = suggestion.getThread().getName().replaceAll("`", "");
            if (name.length() > 50) {
                name = name.substring(0, 50);
                name += "...";
            }

            String link = "[" + name + "](" + suggestion.getThread().getJumpUrl() + ")" +
                (suggestion.isGreenlit() ? " " + getEmojiFormat(EmojiConfig::getGreenlitEmojiId) : "");

            fieldData.add(Arrays.asList(
                link,
                String.valueOf(suggestion.getAgrees()),
                String.valueOf(suggestion.getDisagrees())
            ));
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(
                (tags != null ? "- Filtered by tags: `" + tags + "`\n" : "") + (title != null ? "- Filtered by title: `" + title + "`" : "")
            )
            .addField(
                "Total",
                String.valueOf((int) total),
                true
            )
            .addField(
                getEmojiFormat(EmojiConfig::getGreenlitEmojiId),
                (int) greenlit + " (" + (int) ((greenlit / total) * 100.0) + "%)",
                true
            )
            .addBlankField(true)
            .addField(
                "Links",
                fieldData.stream()
                    .map(list -> list.get(0))
                    .collect(Collectors.joining("\n")),
                true
            )
            .addField(
                getEmojiFormat(EmojiConfig::getAgreeEmojiId),
                fieldData.stream()
                    .map(list -> list.get(1))
                    .collect(Collectors.joining("\n")),
                true
            )
            .addField(
                getEmojiFormat(EmojiConfig::getDisagreeEmojiId),
                fieldData.stream()
                    .map(list -> list.get(2))
                    .collect(Collectors.joining("\n")),
                true
            )
            .setFooter("Page: " + pageNum + "/" + totalPages + " | Alpha: " + (alpha ? "Yes" : "No"));

        return embedBuilder;
    }

    public static Pair<EmbedBuilder, EmbedBuilder> getActivityEmbeds(Member member) {
        Database database = NerdBotApp.getBot().getDatabase();
        DiscordUser discordUser;

        if (NerdBotApp.USER_CACHE.getIfPresent(member.getId()) != null) {
            discordUser = NerdBotApp.USER_CACHE.getIfPresent(member.getId());
        } else {
            discordUser = database.findDocument(database.getCollection("users", DiscordUser.class), "discordId", member.getId()).first();
        }

        // User not found in the database
        if (discordUser == null) {
            return Pair.of(null, null);
        }

        LastActivity lastActivity = discordUser.getLastActivity();
        EmbedBuilder globalEmbedBuilder = new EmbedBuilder();
        EmbedBuilder alphaEmbedBuilder = new EmbedBuilder();

        // Global Activity
        globalEmbedBuilder.setColor(Color.GREEN)
            .setAuthor(member.getEffectiveName() + " (" + member.getId() + ")")
            .setThumbnail(member.getEffectiveAvatarUrl())
            .setTitle("Last Global Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastGlobalActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getLastVoiceChannelJoinDate), true)
            .addField("Item Generator", lastActivity.toRelativeTimestamp(LastActivity::getLastItemGenUsage), true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getSuggestionCommentDate), true);

        // Alpha Activity
        alphaEmbedBuilder.setColor(Color.RED)
            .setTitle("Last Alpha Activity")
            .addField("Most Recent", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaActivity), true)
            .addField("Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getAlphaVoiceJoinDate), true)
            .addBlankField(true)
            // Suggestions
            .addField("Created Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaSuggestionDate), true)
            .addField("Voted on Suggestion", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionVoteDate), true)
            .addField("New Comment", lastActivity.toRelativeTimestamp(LastActivity::getAlphaSuggestionCommentDate), true);

        return Pair.of(globalEmbedBuilder, alphaEmbedBuilder);
    }

    private static String getEmojiFormat(Function<EmojiConfig, String> emojiIdFunction) {
        return getEmojiFormat(emojiIdFunction.apply(NerdBotApp.getBot().getConfig().getEmojiConfig()));
    }

    private static String getEmojiFormat(String emojiId) {
        return NerdBotApp.getBot().getJDA().getEmojiById(emojiId).getFormatted();
    }

    @RequiredArgsConstructor
    private static class Suggestion {

        @Getter
        private final ThreadChannel thread;
        @Getter private final boolean alpha;
        @Getter private final int agrees;
        @Getter private final int disagrees;
        @Getter private final boolean greenlit;

        public Suggestion(ThreadChannel thread, boolean alpha) {
            this.thread = thread;
            this.alpha = alpha;
            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);
            this.agrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getAgreeEmojiId());
            this.disagrees = getReactionCount(message, NerdBotApp.getBot().getConfig().getEmojiConfig().getDisagreeEmojiId());
            this.greenlit = thread.getAppliedTags().stream().anyMatch(forumTag -> GREENLIT_TAGS.contains(forumTag.getName().toLowerCase()));
        }

        public static int getReactionCount(Message message, String emojiId) {
            return message.getReactions()
                .stream()
                .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                .filter(reaction -> reaction.getEmoji()
                    .asCustom()
                    .getId()
                    .equalsIgnoreCase(emojiId)
                )
                .mapToInt(MessageReaction::getCount)
                .findFirst()
                .orElse(0);
        }

    }

}
