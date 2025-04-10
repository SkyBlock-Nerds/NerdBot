package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.internalapi.database.model.reminder.Reminder;
import net.hypixel.nerdbot.internalapi.database.model.user.DiscordUser;
import net.hypixel.nerdbot.internalapi.language.TranslationManager;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class ReminderCommands extends ApplicationCommand {

    private static final Pattern DURATION = Pattern.compile("((\\d+)w)?((\\d+)d)?((\\d+)h)?((\\d+)m)?((\\d+)s)?");

    /**
     * Parse a time string in the format of {@code 1w2d3h4m5s} into a Date
     *
     * @param time The time string to parse
     *
     * @return The parsed string as a Date
     *
     * @throws DateTimeParseException If the string could not be parsed
     */
    public static Date parseCustomFormat(String time) throws DateTimeParseException {
        Matcher matcher = DURATION.matcher(time);

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

    private Date parseLong(String time) throws NumberFormatException {
        return new Date(Long.parseLong(time));
    }

    private Date parseWithNatty(String time) {
        try {
            Parser parser = new Parser();
            List<DateGroup> groups = parser.parse(time);
            DateGroup group = groups.get(0);
            List<Date> dates = group.getDates();
            return dates.get(0);
        } catch (IndexOutOfBoundsException e) {
            // If Natty parsing fails, try custom format
            return parseCustomFormat(time);
        } catch (NumberFormatException exception) {
            throw new DateTimeParseException("Could not parse date: " + time, time, 0);
        }
    }

    @JDASlashCommand(name = "remind", subcommand = "create", description = "Set a reminder")
    public void createReminder(GuildSlashEvent event, @AppOption(description = "Use a format such as \"in 1 hour\" or \"1w3d7h\"") String time, @AppOption String description) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getUser().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        // Check if the bot has permission to send messages in the channel
        if (!event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
            TranslationManager.edit(event.getHook(), user, "permissions.cannot_send_messages");
            return;
        }

        if (event.getChannel() instanceof ThreadChannel && !event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND_IN_THREADS)) {
            TranslationManager.edit(event.getHook(), user, "permissions.cannot_send_messages_in_threads");
            return;
        }

        if (description == null) {
            TranslationManager.edit(event.getHook(), user, "commands.reminders.no_description");
            return;
        }

        if (description.length() > 4_096) {
            TranslationManager.edit(event.getHook(), user, "commands.reminders.description_too_long", 4_096);
            return;
        }

        Date date;
        try {
            date = parseLong(time);
        } catch (NumberFormatException numberFormatException) {
            try {
                date = parseWithNatty(time);
            } catch (DateTimeParseException | NumberFormatException exception) {
                TranslationManager.edit(event.getHook(), user, "commands.reminders.invalid_time_format");
                return;
            }
        }

        // Check if the provided time is in the past
        if (date.before(new Date())) {
            TranslationManager.edit(event.getHook(), user, "commands.reminders.time_in_past");
            return;
        }

        // Create a new reminder and save it to the database
        ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        Reminder reminder = new Reminder(description, date, event.getChannel().getId(), event.getUser().getId());

        reminderRepository.cacheObject(reminder);
        UpdateResult result = reminderRepository.saveToDatabase(reminder);

        // Check if the reminder was saved successfully, schedule it and send a confirmation message
        if (result != null && result.wasAcknowledged() && result.getUpsertedId() != null) {
            MessageEditBuilder builder = new MessageEditBuilder();
            builder.setEmbeds(new EmbedBuilder().setDescription(description).build());
            builder.setContent(TranslationManager.translate(user, "commands.reminders.reminder_set", DiscordTimestamp.toLongDateTime(date.getTime())));
            event.getHook().editOriginal(builder.build()).complete();

            // Sending a nice confirmation message within the dms, so it doesn't disappear.
            PrivateChannel channel = event.getMember().getUser().openPrivateChannel().complete();
            EmbedBuilder embedBuilder = new EmbedBuilder().setDescription(description)
                .setTimestamp(Instant.now())
                .setFooter(reminder.getUuid().toString())
                .setColor(Color.GREEN);

            channel.sendMessage("Reminder set for: " + DiscordTimestamp.toLongDateTime(date.getTime()))
                .addEmbeds(embedBuilder.build())
                .setSuppressedNotifications(true)
                .queue();
            reminder.schedule();
        } else {
            // If the reminder could not be saved, send an error message and log the error too
            TranslationManager.edit(event.getHook(), user, "commands.reminders.save_error");
            log.error("Could not save reminder: " + reminder + " for user: " + event.getUser().getId() + " (" + result + ")");
        }
    }

    @JDASlashCommand(name = "remind", subcommand = "list", description = "View your reminders")
    public void listReminders(GuildSlashEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getUser().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        List<Reminder> reminders = new ArrayList<>(reminderRepository
            .filter(reminder -> reminder.getUserId().equalsIgnoreCase(event.getUser().getId()))
            .stream()
            .toList());

        if (reminders.isEmpty()) {
            TranslationManager.edit(event.getHook(), user, "commands.reminders.no_reminders");
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
            builder.append("(")
                .append(reminder.getUuid())
                .append(") ")
                .append(DiscordTimestamp.toLongDateTime(reminder.getTime().getTime()))
                .append(" - `")
                .append(reminder.getDescription())
                .append("`\n");
        }

        builder.append("\n**")
            .append(TranslationManager.translate(user, "commands.reminders.delete_reminder"))
            .append("**");

        event.getHook().editOriginal(builder.toString()).queue();
    }

    @JDASlashCommand(name = "remind", subcommand = "delete", description = "Delete a reminder")
    public void deleteReminder(GuildSlashEvent event, @AppOption String uuid) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getUser().getId());

        if (user == null) {
            TranslationManager.edit(event.getHook(), "generic.user_not_found");
            return;
        }

        try {
            UUID parsed = UUID.fromString(uuid);
            ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            Reminder reminder = reminderRepository.findById(parsed.toString());

            // Check if the reminder exists first
            if (reminder == null || !reminder.getUserId().equalsIgnoreCase(event.getUser().getId())) {
                TranslationManager.edit(event.getHook(), user, "commands.reminders.reminder_not_found");
                return;
            }

            DeleteResult result = reminderRepository.deleteFromDatabase(reminder.getUuid().toString());

            // Check if the reminder was deleted successfully, cancel the timer and send a confirmation message
            if (result != null && result.wasAcknowledged()) {
                if (reminder.getTimer() != null) {
                    reminder.getTimer().cancel();
                }

                TranslationManager.edit(event.getHook(), user, "commands.reminders.reminder_deleted", reminder.getUuid());
                log.info("Deleted reminder: " + uuid + " for user: " + event.getUser().getId());
            } else {
                // If the reminder could not be deleted, send an error message and log the error
                TranslationManager.edit(event.getHook(), user, "commands.reminders.delete_error", uuid);
                log.info("Could not delete reminder " + uuid + " for user: " + event.getUser().getId() + " (" + result + ")");
            }
        } catch (IllegalArgumentException exception) {
            TranslationManager.edit(event.getHook(), user, "commands.invalid_uuid");
        }
    }
}
