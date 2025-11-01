package net.hypixel.nerdbot.app.command;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import com.mongodb.client.result.DeleteResult;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.hypixel.nerdbot.app.reminder.ReminderDispatcher;
import net.hypixel.nerdbot.core.DiscordTimestamp;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.storage.database.model.reminder.Reminder;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.storage.database.repository.ReminderRepository;

import java.awt.Color;
import java.text.SimpleDateFormat;
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

    @SlashCommand(name = "reminders", subcommand = "view", description = "Create, view, and delete reminders", guildOnly = true)
    public void reminders(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();
        createReminderPanel(event.getHook(), event.getUser().getId(), 1);
    }

    @SlashCommand(name = "reminders", subcommand = "create", description = "Quick shortcut to create a new reminder", guildOnly = true)
    public void remindersCreate(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        Modal modal = createReminderModal(userId);
        event.replyModal(modal).queue();
    }

    @SlashComponentHandler(id = "reminder-nav", patterns = {"reminder-nav-*"})
    public void handleReminderNavigation(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String action = parts[2]; // "prev", "next"
        int page = Integer.parseInt(parts[3]);
        String userId = parts[4];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own reminder interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        int newPage = switch (action) {
            case "prev" -> Math.max(1, page - 1);
            case "next" -> page + 1;
            default -> page;
        };

        createReminderPanel(event.getHook(), userId, newPage);
    }

    @SlashComponentHandler(id = "reminder-manage", patterns = {"reminder-manage-*"})
    public void handleReminderManage(StringSelectInteractionEvent event) {
        String userId = event.getComponentId().split("-")[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own reminder interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String reminderUuid = event.getValues().get(0);
        showReminderDetail(event.getHook(), userId, reminderUuid);
    }

    @SlashComponentHandler(id = "reminder-detail", patterns = {"reminder-detail-*"})
    public void handleReminderDetail(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        // Parse: reminder-detail-{action}-{uuid}-{userId}
        // We need to be careful because UUIDs contain dashes
        String[] parts = componentId.split("-");
        if (parts.length < 8) { // reminder-detail-action + 5 UUID parts + userId = 8
            event.reply("Invalid component ID format!").setEphemeral(true).queue();
            return;
        }

        String action = parts[2]; // "delete", "back"

        // UUID is parts[3] to parts[7]
        String reminderUuid = String.join("-", parts[3], parts[4], parts[5], parts[6], parts[7]);

        // userId is the last part
        String userId = parts[parts.length - 1];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own reminder interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        switch (action) {
            case "delete" -> {
                try {
                    UUID parsed = UUID.fromString(reminderUuid);
                    ReminderRepository reminderRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
                    Reminder reminder = reminderRepository.findById(parsed.toString());

                    if (reminder == null || !reminder.getUserId().equalsIgnoreCase(userId)) {
                        createReminderPanel(event.getHook(), userId, 1, "❌ Reminder not found!");
                        return;
                    }

                    DeleteResult result = reminderRepository.deleteFromDatabase(reminder.getUuid().toString());

                    if (result != null && result.wasAcknowledged()) {
                        if (reminder.getTimer() != null) {
                            reminder.getTimer().cancel();
                        }
                        createReminderPanel(event.getHook(), userId, 1, "✅ Successfully deleted reminder!");
                        log.info("Deleted reminder: {} for user: {}", reminderUuid, userId);
                    } else {
                        createReminderPanel(event.getHook(), userId, 1, "❌ Failed to delete reminder. Please try again!");
                        log.error("Could not delete reminder {} for user: {} ({})", reminderUuid, userId, result);
                    }
                } catch (IllegalArgumentException exception) {
                    createReminderPanel(event.getHook(), userId, 1, "❌ Invalid reminder ID!");
                }
            }
            case "back" -> createReminderPanel(event.getHook(), userId, 1);
        }
    }

    @SlashComponentHandler(id = "reminder-create", patterns = {"reminder-create-*"})
    public void handleReminderCreate(ButtonInteractionEvent event) {
        String userId = event.getComponentId().split("-")[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own reminder interface!").setEphemeral(true).queue();
            return;
        }

        // Create and show the modal
        Modal modal = createReminderModal(userId);
        event.replyModal(modal).queue();
    }

    @SlashModalHandler(id = "reminder-modal", patterns = {"reminder-modal-*"})
    public void handleReminderModal(ModalInteractionEvent event) {
        String userId = event.getModalId().split("-")[2];
        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own reminder interface!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        if (event.getValues().isEmpty()) {
            event.getHook().editOriginal("❌ No input provided! Please try again.").queue();
            return;
        }

        if (event.getValues().size() < 2) {
            event.getHook().editOriginal("❌ Missing required fields! Please fill out all fields.").queue();
            return;
        }

        String timeInput = event.getValue("time").getAsString();
        String descriptionInput = event.getValue("description").getAsString();

        createReminderFromModalReply(event.getHook(), userId, timeInput, descriptionInput, event.getGuild(), event.getChannel(), event.getMember());
    }

    private void createReminderFromModalReply(InteractionHook hook, String userId, String timeInput, String descriptionInput, Guild guild, MessageChannelUnion channel, Member member) {
        DiscordUserRepository userRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        userRepository.findByIdAsync(userId)
            .thenAccept(user -> {
                if (user == null) {
                    hook.editOriginal("❌ User not found!").queue();
                    return;
                }

                if (!guild.getSelfMember().hasPermission(channel.asGuildMessageChannel(), Permission.MESSAGE_SEND)) {
                    hook.editOriginal("❌ I don't have permission to send messages in this channel!").queue();
                    return;
                }

                if (channel instanceof ThreadChannel && !guild.getSelfMember().hasPermission(channel.asGuildMessageChannel(), Permission.MESSAGE_SEND_IN_THREADS)) {
                    hook.editOriginal("❌ I don't have permission to send messages in threads!").queue();
                    return;
                }

                if (descriptionInput == null || descriptionInput.trim().isEmpty()) {
                    hook.editOriginal("❌ You need to provide a description for the reminder!").queue();
                    return;
                }

                if (descriptionInput.length() > 4_000) {
                    hook.editOriginal("❌ Description is too long! Please keep it under 4,000 characters.").queue();
                    return;
                }

                Date date;
                try {
                    date = parseLong(timeInput);
                } catch (NumberFormatException numberFormatException) {
                    try {
                        date = parseWithNatty(timeInput);
                    } catch (DateTimeParseException | NumberFormatException exception) {
                        hook.editOriginal("❌ I could not parse that date/time format! Please try again.").queue();
                        return;
                    }
                }

                // Check if the provided time is in the past
                if (date.before(new Date())) {
                    hook.editOriginal("❌ You cannot set a reminder in the past!").queue();
                    return;
                }

                ReminderRepository reminderRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
                Reminder reminder = new Reminder(descriptionInput, date, channel.getId(), userId);

                final Date finalDate = date;
                final String finalDescription = descriptionInput;

                reminderRepository.cacheObject(reminder);

                reminderRepository.saveToDatabaseAsync(reminder)
                    .thenAccept(result -> {
                        if (result != null && result.wasAcknowledged() && result.getUpsertedId() != null) {
                            ReminderDispatcher.schedule(reminder);

                            // Send confirmation DM
                            try {
                                PrivateChannel privateChannel = member.getUser().openPrivateChannel().complete();
                                EmbedBuilder embedBuilder = new EmbedBuilder()
                                    .setDescription(finalDescription)
                                    .setTimestamp(Instant.now())
                                    .setFooter(reminder.getUuid().toString())
                                    .setColor(Color.GREEN);

                                privateChannel.sendMessage("Reminder set for: " + DiscordTimestamp.toLongDateTime(finalDate.getTime()))
                                    .addEmbeds(embedBuilder.build())
                                    .setSuppressedNotifications(true)
                                    .queue();
                            } catch (Exception e) {
                                log.error("Failed to send reminder DM to user: {}", userId, e);
                            }

                            hook.editOriginal("✅ Successfully created reminder for " + DiscordTimestamp.toLongDateTime(finalDate.getTime()) + "!\n\n" +
                                "📱 Use `/reminders view` to view and manage all your reminders.").queue();
                        } else {
                            hook.editOriginal("❌ An error occurred while saving the reminder! Please try again.").queue();
                            log.error("Could not save reminder: {} for user: {} ({})", reminder, userId, result);
                        }
                    });
            });
    }

    private void showReminderDetail(InteractionHook hook, String userId, String reminderUuid) {
        try {
            UUID parsed = UUID.fromString(reminderUuid);
            ReminderRepository reminderRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
            Reminder reminder = reminderRepository.findById(parsed.toString());

            if (reminder == null || !reminder.getUserId().equalsIgnoreCase(userId)) {
                createReminderPanel(hook, userId, 1, "❌ Reminder not found!");
                return;
            }

            String content = "📋 **Reminder Details**\n\n" +
                "🕐 **Time:** " + DiscordTimestamp.toLongDateTime(reminder.getTime()) + "\n" +
                "⏰ **Relative:** " + DiscordTimestamp.toRelativeTimestamp(reminder.getTime()) + "\n\n" +
                "📝 **Description:**\n" + reminder.getDescription() + "\n\n" +
                "📍 **Channel:** <#" + reminder.getChannelId() + ">";

            ActionRow actionRow = ActionRow.of(
                Button.danger("reminder-detail-delete-" + reminderUuid + "-" + userId, "🗑️ Delete Reminder"),
                Button.secondary("reminder-detail-back-" + reminderUuid + "-" + userId, "⬅️ Back to List")
            );

            hook.editOriginal(content)
                .setComponents(actionRow)
                .queue();
        } catch (IllegalArgumentException exception) {
            createReminderPanel(hook, userId, 1, "❌ Invalid reminder ID!");
        }
    }

    private void createReminderPanel(InteractionHook hook, String userId, int page) {
        createReminderPanel(hook, userId, page, null);
    }

    private void createReminderPanel(InteractionHook hook, String userId, int page, String message) {
        DiscordUserRepository userRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(userId);

        if (user == null) {
            hook.editOriginal("❌ User not found!").queue();
            return;
        }

        ReminderRepository reminderRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(ReminderRepository.class);
        List<Reminder> allReminders = new ArrayList<>(reminderRepository
            .filter(reminder -> reminder.getUserId().equalsIgnoreCase(userId))
            .stream()
            .sorted(Comparator.comparingLong(Reminder::getTime))
            .toList());

        if (allReminders.isEmpty()) {
            String content = "⏰ **Your Reminders**\n\n" +
                (message != null ? message + "\n\n" : "") +
                "You don't have any reminders set!\n\n";

            ActionRow createButton = ActionRow.of(
                Button.primary("reminder-create-" + userId, "Create New Reminder")
            );

            hook.editOriginal(content).setComponents(createButton).queue();
            return;
        }

        // Pagination
        int remindersPerPage = 25;
        int totalPages = (int) Math.ceil((double) allReminders.size() / remindersPerPage);
        page = Math.min(Math.max(1, page), totalPages);

        List<Reminder> pageReminders = allReminders.stream()
            .skip((long) (page - 1) * remindersPerPage)
            .limit(remindersPerPage)
            .toList();

        StringBuilder content = new StringBuilder();
        content.append("⏰ **Your Reminders**\n\n");

        if (message != null) {
            content.append(message).append("\n\n");
        }

        content.append("📊 **Total:** ").append(allReminders.size()).append(" reminders");
        if (totalPages > 1) {
            content.append(" (Page ").append(page).append("/").append(totalPages).append(")");
        }
        content.append("\n\n");
        content.append("Use the dropdown below to select a reminder to view details and manage it.");

        // Build action rows
        List<ActionRow> actionRows = new ArrayList<>();

        // Add reminder management dropdown
        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create("reminder-manage-" + userId)
            .setPlaceholder("Select a reminder to manage...")
            .setRequiredRange(1, 1);

        for (int i = 0; i < pageReminders.size(); i++) {
            Reminder reminder = pageReminders.get(i);
            int globalIndex = (page - 1) * remindersPerPage + i + 1;

            // Use plain text date format instead of Discord timestamp for selection menu
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a z");
            String readableDate = dateFormat.format(reminder.getTime());

            String optionLabel = "#" + globalIndex + " - " + readableDate;
            String optionDescription = reminder.getDescription().length() > 50
                ? reminder.getDescription().substring(0, 47) + "..."
                : reminder.getDescription();

            selectBuilder.addOption(optionLabel, reminder.getUuid().toString(), optionDescription);
        }

        actionRows.add(ActionRow.of(selectBuilder.build()));

        // Add navigation and create buttons
        List<Button> navButtons = new ArrayList<>();

        if (totalPages > 1) {
            navButtons.add(Button.secondary("reminder-nav-prev-" + page + "-" + userId, "◀️ Previous")
                .withDisabled(page <= 1));
            navButtons.add(Button.secondary("reminder-nav-next-" + page + "-" + userId, "Next ▶️")
                .withDisabled(page >= totalPages));
        }

        navButtons.add(Button.primary("reminder-create-" + userId, "Create New Reminder"));
        actionRows.add(ActionRow.of(navButtons));

        hook.editOriginal(content.toString())
            .setComponents(actionRows)
            .queue();
    }

    private Modal createReminderModal(String userId) {
        TextInput timeInput = TextInput.create("time", "Time", TextInputStyle.SHORT)
            .setPlaceholder("e.g., \"in 1 hour\", \"1w3d7h\", \"tomorrow at 3pm\"")
            .setRequiredRange(1, 100)
            .build();

        TextInput descriptionInput = TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("What should I remind you about?")
            .setRequiredRange(1, 4_000)
            .build();

        return Modal.create("reminder-modal-" + userId, "⏰ Create New Reminder")
            .addComponents(ActionRow.of(timeInput), ActionRow.of(descriptionInput))
            .build();
    }
}