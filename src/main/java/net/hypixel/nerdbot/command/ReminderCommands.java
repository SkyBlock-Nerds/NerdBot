package net.hypixel.nerdbot.command;

import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.client.result.DeleteResult;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.reminder.Reminder;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;

import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.ReminderRepository;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class ReminderCommands {

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

    @SlashCommand(name = "remind", subcommand = "create", description = "Set a reminder", guildOnly = true)
    public void createReminder(SlashCommandInteractionEvent event, @SlashOption(description = "Use a format such as \"in 1 hour\" or \"1w3d7h\"") String time, @SlashOption String description) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        
        userRepository.findByIdAsync(event.getUser().getId())
            .thenAccept(user -> {
                if (user == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                // Check if the bot has permission to send messages in the channel
                if (!event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND)) {
                    event.getHook().editOriginal("I do not have the correct permission to send messages in this channel!").queue();
                    return;
                }

                if (event.getChannel() instanceof ThreadChannel && !event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.MESSAGE_SEND_IN_THREADS)) {
                    event.getHook().editOriginal("I do not have the correct permission to send messages in threads!").queue();
                    return;
                }

                if (description == null) {
                    event.getHook().editOriginal("You need to provide a description for the reminder!").queue();
                    return;
                }

                if (description.length() > 4_096) {
                    event.getHook().editOriginal(String.format("Your description is too long! Please keep it under %d characters.", 4_096)).queue();
                    return;
                }

                Date date;
                try {
                    date = parseLong(time);
                } catch (NumberFormatException numberFormatException) {
                    try {
                        date = parseWithNatty(time);
                    } catch (DateTimeParseException | NumberFormatException exception) {
                        event.getHook().editOriginal("I could not parse that date/time format, please try again!").queue();
                        return;
                    }
                }

                // Check if the provided time is in the past
                if (date.before(new Date())) {
                    event.getHook().editOriginal("You cannot set a reminder in the past!").queue();
                    return;
                }

                // Create a new reminder and save it to the database
                ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
                Reminder reminder = new Reminder(description, date, event.getChannel().getId(), event.getUser().getId());

                reminderRepository.cacheObject(reminder);

                final Date finalDate = date;
                reminderRepository.saveToDatabaseAsync(reminder)
                    .thenAccept(result -> {
                        // Check if the reminder was saved successfully, schedule it and send a confirmation message
                        if (result != null && result.wasAcknowledged() && result.getUpsertedId() != null) {
                            MessageEditBuilder builder = new MessageEditBuilder();
                            builder.setEmbeds(new EmbedBuilder().setDescription(description).build())
                                .setContent(String.format("I will remind you at %s about:", DiscordTimestamp.toLongDateTime(finalDate.getTime())));

                            event.getHook().editOriginal(builder.build()).complete();
                            reminder.schedule();

                            try {
                                // Sending a nice confirmation message within the dms, so it doesn't disappear.
                                PrivateChannel channel = event.getMember().getUser().openPrivateChannel().complete();
                                EmbedBuilder embedBuilder = new EmbedBuilder().setDescription(description)
                                    .setTimestamp(Instant.now())
                                    .setFooter(reminder.getUuid().toString())
                                    .setColor(Color.GREEN);

                                channel.sendMessage("Reminder set for: " + DiscordTimestamp.toLongDateTime(finalDate.getTime()))
                                    .addEmbeds(embedBuilder.build())
                                    .setSuppressedNotifications(true)
                                    .queue();
                            } catch (Exception e) {
                                log.error("Failed to send reminder DM to user: " + event.getUser().getId(), e);
                                event.getHook().editOriginal("Failed to send reminder DM!").queue();
                            }
                        } else {
                            // If the reminder could not be saved, send an error message and log the error too
                            event.getHook().editOriginal("An error occurred while saving that reminder! Please try again!").queue();
                            log.error("Could not save reminder: " + reminder + " for user: " + event.getUser().getId() + " (" + result + ")");
                        }
                    });
            });
    }

    @SlashCommand(name = "remind", subcommand = "list", description = "View your reminders", guildOnly = true)
    public void listReminders(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        
        userRepository.findByIdAsync(event.getUser().getId())
            .thenAccept(user -> {
                if (user == null) {
                    event.getHook().editOriginal("User not found").queue();
                    return;
                }

                ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
                List<Reminder> reminders = new ArrayList<>(reminderRepository
                    .filter(reminder -> reminder.getUserId().equalsIgnoreCase(event.getUser().getId()))
                    .stream()
                    .toList());

                if (reminders.isEmpty()) {
                    event.getHook().editOriginal("You do not have any reminders set!").queue();
                    return;
                }

                // Sort reminders by date (earliest first)
                reminders.sort(Comparator.comparing(Reminder::getTime));

                EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.setTitle(String.format("Your Reminders"));
                embedBuilder.setColor(Color.BLUE);
                
                for (int i = 0; i < Math.min(reminders.size(), 25); i++) {
                    Reminder reminder = reminders.get(i);
                    embedBuilder.addField(
                        String.format("%d. %s", i + 1, DiscordTimestamp.toShortDateTime(reminder.getTime().getTime())),
                        reminder.getDescription().substring(0, Math.min(reminder.getDescription().length(), 1000)) + 
                        (reminder.getDescription().length() > 1000 ? "..." : ""),
                        false
                    );
                }
                
                if (reminders.size() > 25) {
                    embedBuilder.setFooter(String.format("Showing first 25 of %d reminders", reminders.size()));
                }
                
                event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
            });
    }

    @SlashCommand(name = "remind", subcommand = "delete", description = "Delete a reminder", guildOnly = true)
    public void deleteReminder(SlashCommandInteractionEvent event, @SlashOption String uuid) {
        event.deferReply(true).complete();

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getUser().getId());

        if (user == null) {
            event.getHook().editOriginal("User not found").queue();
            return;
        }

        try {
            UUID parsed = UUID.fromString(uuid);
            ReminderRepository reminderRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            Reminder reminder = reminderRepository.findById(parsed.toString());

            // Check if the reminder exists first
            if (reminder == null || !reminder.getUserId().equalsIgnoreCase(event.getUser().getId())) {
                event.getHook().editOriginal("I could not find a reminder with that UUID!").queue();
                return;
            }

            DeleteResult result = reminderRepository.deleteFromDatabase(reminder.getUuid().toString());

            // Check if the reminder was deleted successfully, cancel the timer and send a confirmation message
            if (result != null && result.wasAcknowledged()) {
                if (reminder.getTimer() != null) {
                    reminder.getTimer().cancel();
                }

                event.getHook().editOriginal(String.format("Deleted reminder %s!", reminder.getUuid())).queue();
                log.info("Deleted reminder: " + uuid + " for user: " + event.getUser().getId());
            } else {
                // If the reminder could not be deleted, send an error message and log the error
                event.getHook().editOriginal(String.format("An error occurred while deleting that reminder! Please try again!")).queue();
                log.info("Could not delete reminder " + uuid + " for user: " + event.getUser().getId() + " (" + result + ")");
            }
        } catch (IllegalArgumentException exception) {
            event.getHook().editOriginal("Invalid UUID!").queue();
        }
    }
}
