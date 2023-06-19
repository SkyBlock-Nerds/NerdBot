package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.reminder.Reminder;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

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
public class UserCommands extends ApplicationCommand {

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
}
