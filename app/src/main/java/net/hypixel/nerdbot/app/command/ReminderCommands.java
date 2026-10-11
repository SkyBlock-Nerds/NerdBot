package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IModalCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.reminder.ReminderService;
import net.hypixel.nerdbot.app.reminder.ReminderService.CreateResult;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code /reminders} commands and the panel they open. Each handler hands its event to a method
 * that takes the matching JDA interface, so tests can drive them with fakes.
 */
@Slf4j
public class ReminderCommands {

    static final int REMINDERS_PER_PAGE = 25;
    static final String NOT_YOUR_PANEL = "You can only use your own reminder interface!";

    static final String NAV_PREFIX = "reminder-nav";
    static final String MANAGE_PREFIX = "reminder-manage";
    static final String DETAIL_PREFIX = "reminder-detail";
    static final String CREATE_PREFIX = "reminder-create";
    static final String MODAL_PREFIX = "reminder-modal";

    static final PanelId NAV = PanelId.of(NAV_PREFIX, 3, 2);
    static final PanelId MANAGE = PanelId.of(MANAGE_PREFIX, 1, 0);
    static final PanelId DETAIL = new PanelId(DETAIL_PREFIX, 3, 2, 1);
    static final PanelId CREATE = PanelId.of(CREATE_PREFIX, 1, 0);
    static final PanelId MODAL = PanelId.of(MODAL_PREFIX, 1, 0);

    // Plain text because select menu options can't render Discord timestamps, e.g. "Apr 02, 2026 at 3:45 PM UTC".
    // The locale is fixed so the server's own locale can't change it (en_GB would print "pm").
    private static final DateTimeFormatter MENU_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a z", Locale.US)
        .withZone(ZoneId.of("UTC"));

    private final ReminderService reminders;

    @Inject
    public ReminderCommands(ReminderService reminders) {
        this.reminders = reminders;
    }

    @SlashCommand(name = "reminders", subcommand = "view", description = "Create, view, and delete reminders", guildOnly = true)
    public void reminders(SlashCommandInteractionEvent event) {
        showPanel(event);
    }

    @SlashCommand(name = "reminders", subcommand = "create", description = "Quick shortcut to create a new reminder", guildOnly = true)
    public void remindersCreate(SlashCommandInteractionEvent event) {
        openCreateModal(event, event.getUser().getId());
    }

    @SlashComponentHandler(id = NAV_PREFIX, patterns = {NAV_PREFIX + "-*"})
    public void handleReminderNavigation(ButtonInteractionEvent event) {
        navigate(event);
    }

    @SlashComponentHandler(id = MANAGE_PREFIX, patterns = {MANAGE_PREFIX + "-*"})
    public void handleReminderManage(StringSelectInteractionEvent event) {
        selectReminder(event);
    }

    @SlashComponentHandler(id = DETAIL_PREFIX, patterns = {DETAIL_PREFIX + "-*"})
    public void handleReminderDetail(ButtonInteractionEvent event) {
        detailAction(event);
    }

    @SlashComponentHandler(id = CREATE_PREFIX, patterns = {CREATE_PREFIX + "-*"})
    public void handleReminderCreate(ButtonInteractionEvent event) {
        createButton(event);
    }

    @SlashModalHandler(id = MODAL_PREFIX, patterns = {MODAL_PREFIX + "-*"})
    public void handleReminderModal(ModalInteractionEvent event) {
        submit(event);
    }

    void showPanel(IReplyCallback interaction) {
        interaction.deferReply(true).queue();
        renderPanel(interaction.getHook(), interaction.getUser().getId(), 1, null);
    }

    void navigate(ButtonInteraction interaction) {
        Optional<List<String>> parts = NAV.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }

        int page;
        try {
            page = Integer.parseInt(parts.get().get(1));
        } catch (NumberFormatException exception) {
            interaction.reply(PanelId.OUTDATED).setEphemeral(true).queue();
            return;
        }

        int newPage = switch (parts.get().get(0)) {
            case "prev" -> page - 1;
            case "next" -> page + 1;
            default -> page;
        };

        interaction.deferEdit().queue();
        renderPanel(interaction.getHook(), parts.get().get(2), newPage, null);
    }

    void selectReminder(StringSelectInteraction interaction) {
        Optional<List<String>> parts = MANAGE.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty() || interaction.getValues().isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderDetail(interaction.getHook(), parts.get().getFirst(), interaction.getValues().getFirst());
    }

    void detailAction(ButtonInteraction interaction) {
        Optional<List<String>> parts = DETAIL.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }
        String action = parts.get().get(0);
        String reminderId = parts.get().get(1);
        String userId = parts.get().get(2);

        interaction.deferEdit().queue();

        if (!action.equals("delete")) {
            renderPanel(interaction.getHook(), userId, 1, null);
            return;
        }

        switch (reminders.delete(userId, reminderId)) {
            case DELETED -> {
                log.info("Deleted reminder {} for user {}", reminderId, userId);
                renderPanel(interaction.getHook(), userId, 1, "✅ Successfully deleted reminder!");
            }
            case NOT_FOUND -> renderPanel(interaction.getHook(), userId, 1, "❌ Reminder not found!");
        }
    }

    void createButton(ButtonInteraction interaction) {
        Optional<List<String>> parts = CREATE.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isPresent()) {
            openCreateModal(interaction, parts.get().getFirst());
        }
    }

    void submit(ModalInteraction interaction) {
        Optional<List<String>> parts = MODAL.parseFor(interaction, interaction.getModalId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }
        String userId = parts.get().getFirst();

        interaction.deferReply(true).queue();
        InteractionHook hook = interaction.getHook();

        String timeInput = modalText(interaction, "time");
        String description = modalText(interaction, "description");
        if (timeInput == null || description == null) {
            hook.editOriginal("❌ Missing required fields! Please fill out all fields.").queue();
            return;
        }

        Optional<String> problem = sendingProblem(interaction.getGuild(), interaction.getChannel());
        if (problem.isPresent()) {
            hook.editOriginal(problem.get()).queue();
            return;
        }

        reminders.create(userId, interaction.getChannel().getId(), timeInput, description).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Could not save reminder for user {}", userId, error);
                hook.editOriginal("❌ An error occurred while saving the reminder! Please try again.").queue();
                return;
            }

            switch (result) {
                case CreateResult.Created created -> hook.editOriginal("✅ Successfully created reminder for "
                    + DiscordTimestamp.toLongDateTime(created.reminder().getTime()) + "!\n\n"
                    + "📱 Use `/reminders view` to view and manage all your reminders.").queue();
                case CreateResult.Rejected rejected -> hook.editOriginal(rejected.reason()).queue();
            }
        });
    }

    private static void openCreateModal(IModalCallback interaction, String userId) {
        TextInput timeInput = TextInput.create("time", "Time", TextInputStyle.SHORT)
            .setPlaceholder("e.g., \"in 1 hour\", \"1w3d7h\", \"tomorrow at 3pm\"")
            .setRequiredRange(1, 100)
            .build();

        TextInput descriptionInput = TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("What should I remind you about?")
            .setRequiredRange(1, ReminderService.MAX_DESCRIPTION_LENGTH)
            .build();

        Modal modal = Modal.create(MODAL.build(userId), "⏰ Create New Reminder")
            .addComponents(ActionRow.of(timeInput), ActionRow.of(descriptionInput))
            .build();

        interaction.replyModal(modal).queue();
    }

    /**
     * Why the bot couldn't post in this channel, if it couldn't. Reminders arrive by DM, but the
     * channel is stored with them, so creating one still needs a channel the bot can use.
     */
    static Optional<String> sendingProblem(@Nullable Guild guild, @Nullable MessageChannelUnion channel) {
        if (guild == null || channel == null || !channel.getType().isGuild() || !channel.getType().isMessage()) {
            return Optional.of("❌ Reminders can only be created from a server channel!");
        }

        GuildMessageChannel target = channel.asGuildMessageChannel();
        Member self = guild.getSelfMember();
        if (!self.hasPermission(target, Permission.MESSAGE_SEND)) {
            return Optional.of("❌ I don't have permission to send messages in this channel!");
        }

        if (channel.getType().isThread() && !self.hasPermission(target, Permission.MESSAGE_SEND_IN_THREADS)) {
            return Optional.of("❌ I don't have permission to send messages in threads!");
        }

        return Optional.empty();
    }

    @Nullable
    private static String modalText(ModalInteraction interaction, String inputId) {
        ModalMapping mapping = interaction.getValue(inputId);
        return mapping == null ? null : mapping.getAsString();
    }

    private void renderDetail(InteractionHook hook, String userId, String reminderId) {
        Optional<Reminder> found = reminders.find(userId, reminderId);
        if (found.isEmpty()) {
            renderPanel(hook, userId, 1, "❌ Reminder not found!");
            return;
        }

        Reminder reminder = found.get();
        String content = "📋 **Reminder Details**\n\n" +
            "🕐 **Time:** " + DiscordTimestamp.toLongDateTime(reminder.getTime()) + "\n" +
            "⏰ **Relative:** " + DiscordTimestamp.toRelativeTimestamp(reminder.getTime()) + "\n\n" +
            "📝 **Description:**\n" + reminder.getDescription() + "\n\n" +
            "📍 **Channel:** <#" + reminder.getChannelId() + ">";

        String uuid = reminder.getUuid().toString();
        ActionRow actionRow = ActionRow.of(
            Button.danger(DETAIL.build("delete", uuid, userId), "🗑️ Delete Reminder"),
            Button.secondary(DETAIL.build("back", uuid, userId), "⬅️ Back to List")
        );

        hook.editOriginal(content).setComponents(actionRow).queue();
    }

    private void renderPanel(InteractionHook hook, String userId, int page, @Nullable String message) {
        List<Reminder> all = reminders.remindersFor(userId);
        String header = "⏰ **Your Reminders**\n\n" + (message != null ? message + "\n\n" : "");
        Button createButton = Button.primary(CREATE.build(userId), "Create New Reminder");

        if (all.isEmpty()) {
            hook.editOriginal(header + "You don't have any reminders set!\n\n").setComponents(ActionRow.of(createButton)).queue();
            return;
        }

        int totalPages = (all.size() + REMINDERS_PER_PAGE - 1) / REMINDERS_PER_PAGE;
        int shownPage = Math.clamp(page, 1, totalPages);
        int firstIndex = (shownPage - 1) * REMINDERS_PER_PAGE;
        List<Reminder> pageItems = all.subList(firstIndex, Math.min(firstIndex + REMINDERS_PER_PAGE, all.size()));

        StringBuilder content = new StringBuilder(header)
            .append("📊 **Total:** ").append(all.size()).append(" reminders");
        if (totalPages > 1) {
            content.append(" (Page ").append(shownPage).append("/").append(totalPages).append(")");
        }
        content.append("\n\nUse the dropdown below to select a reminder to view details and manage it.");

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(MANAGE.build(userId))
            .setPlaceholder("Select a reminder to manage...")
            .setRequiredRange(1, 1);

        for (int i = 0; i < pageItems.size(); i++) {
            Reminder reminder = pageItems.get(i);
            String label = "#" + (firstIndex + i + 1) + " - " + TimeUtils.format(Instant.ofEpochMilli(reminder.getTime()), MENU_DATE_FORMAT);
            selectBuilder.addOption(label, reminder.getUuid().toString(), StringUtils.truncate(reminder.getDescription(), 50));
        }

        List<Button> buttons = new ArrayList<>();
        if (totalPages > 1) {
            buttons.add(Button.secondary(NAV.build("prev", shownPage, userId), "◀️ Previous").withDisabled(shownPage <= 1));
            buttons.add(Button.secondary(NAV.build("next", shownPage, userId), "Next ▶️").withDisabled(shownPage >= totalPages));
        }
        buttons.add(createButton);

        hook.editOriginal(content.toString())
            .setComponents(ActionRow.of(selectBuilder.build()), ActionRow.of(buttons))
            .queue();
    }
}
