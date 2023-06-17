package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.InsertOneResult;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.EmbedBuilder;
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
    public void createReminder(GuildSlashEvent event, @AppOption String time, @AppOption String description) {
        Date date;

        try {
            Parser parser = new Parser();
            List<DateGroup> groups = parser.parse(time);
            DateGroup group = groups.get(0);

            List<Date> dates = group.getDates();
            date = dates.get(0);
        } catch (IndexOutOfBoundsException exception) {
            try {
                date = parseCustomFormat(time);
            } catch (DateTimeParseException exception2) {
                event.reply("Could not parse date: " + time).setEphemeral(true).queue();
                return;
            }
        }

        if (date.before(new Date())) {
            event.reply("You can't set a reminder in the past!").setEphemeral(true).queue();
            return;
        }

        Reminder reminder = new Reminder(description, date, event.getChannel().getId(), event.getUser().getId());
        InsertOneResult result = reminder.save();

        if (result != null && result.wasAcknowledged() && result.getInsertedId() != null) {
            event.reply("I will remind you at " + new DiscordTimestamp(date.getTime()).toLongDateTime() + " about:")
                    .addEmbeds(new EmbedBuilder().setDescription(description).build())
                    .setEphemeral(true)
                    .queue();
            reminder.schedule();
        } else {
            event.reply("Could not save your reminder, please try again later!").queue();
            log.error("Could not save reminder: " + reminder + " for user: " + event.getUser().getId() + " (" + result + ")");
        }
    }

    @JDASlashCommand(name = "remind", subcommand = "list", description = "View your reminders")
    public void listReminders(GuildSlashEvent event) {
        List<Reminder> reminders = NerdBotApp.getBot().getDatabase().findAllDocuments(NerdBotApp.getBot().getDatabase().getCollection("reminders", Reminder.class), Filters.eq("userId", event.getUser().getId())).into(new ArrayList<>());

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

        if (reminders.isEmpty()) {
            event.reply("You have no reminders!").setEphemeral(true).queue();
            return;
        }

        StringBuilder builder = new StringBuilder("**Your reminders:**\n");
        for (Reminder reminder : reminders) {
            builder.append("(").append(reminder.getUuid()).append(") ").append(new DiscordTimestamp(reminder.getTime().getTime()).toLongDateTime()).append(" - `").append(reminder.getDescription()).append("`\n");
        }

        builder.append("\n**You can delete reminders with `/remind delete <uuid>`**");
        event.reply(builder.toString()).setEphemeral(true).queue();
    }

    @JDASlashCommand(name = "remind", subcommand = "delete", description = "Delete a reminder")
    public void deleteReminder(GuildSlashEvent event, @AppOption String uuid) {
        UUID parsed = UUID.fromString(uuid);
        Reminder reminder = NerdBotApp.getBot().getDatabase().findDocument(NerdBotApp.getBot().getDatabase().getCollection("reminders", Reminder.class), Filters.and(Filters.eq("userId", event.getUser().getId()), Filters.eq("uuid", parsed))).first();

        if (reminder == null) {
            event.reply("Could not find reminder: " + uuid).setEphemeral(true).queue();
            return;
        }

        if (reminder.getTimer() != null) {
            reminder.getTimer().cancel();
        }

        reminder.delete();
        log.info("Deleted reminder: " + uuid + " for user: " + event.getUser().getId());
        event.reply("Deleted reminder: " + uuid).setEphemeral(true).queue();
    }

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
