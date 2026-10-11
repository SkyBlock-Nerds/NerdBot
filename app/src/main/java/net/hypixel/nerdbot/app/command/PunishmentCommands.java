package net.hypixel.nerdbot.app.command;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashModalHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
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
import net.hypixel.nerdbot.app.channel.LogChannel;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.punishment.PunishmentService;
import net.hypixel.nerdbot.app.punishment.PunishmentStats;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@code /punishment} commands and the panels they open. Each handler hands its event to a
 * method that takes the matching JDA interface, so tests can drive them with fakes.
 */
@Slf4j
public class PunishmentCommands {

    static final int ITEMS_PER_PAGE = 10;
    static final String NOT_YOUR_PANEL = "You can only use your own punishment interface!";

    static final String TYPE_PREFIX = "punish-type";
    static final String MODAL_PREFIX = "punish-modal";
    static final String DETAIL_PREFIX = "punish-detail";
    static final String NAV_PREFIX = "punish-nav";
    static final String DELETE_PREFIX = "punish-delete";
    static final String CONFIRM_YES_PREFIX = "punish-confirm-yes";
    static final String CONFIRM_NO_PREFIX = "punish-confirm-no";
    static final String BACK_PREFIX = "punish-back";

    static final PanelId TYPE = PanelId.of(TYPE_PREFIX, 2, 0);
    static final PanelId MODAL = PanelId.of(MODAL_PREFIX, 3, 0);
    static final PanelId DETAIL = PanelId.of(DETAIL_PREFIX, 2, 0);
    static final PanelId NAV = PanelId.of(NAV_PREFIX, 4, 2);
    static final PanelId DELETE = PanelId.of(DELETE_PREFIX, 2, 0);
    static final PanelId CONFIRM_YES = PanelId.of(CONFIRM_YES_PREFIX, 2, 0);
    static final PanelId CONFIRM_NO = PanelId.of(CONFIRM_NO_PREFIX, 2, 0);
    static final PanelId BACK = PanelId.of(BACK_PREFIX, 2, 0);

    private final PunishmentService punishments;
    private final LogChannel logChannel;
    private final Clock clock;

    @Inject
    public PunishmentCommands(PunishmentService punishments, LogChannel logChannel, Clock clock) {
        this.punishments = punishments;
        this.logChannel = logChannel;
        this.clock = clock;
    }

    @SlashCommand(name = "punishment", subcommand = "create", description = "Record a new punishment for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentCreate(SlashCommandInteractionEvent event, @SlashOption Member member) {
        startCreate(event, member);
    }

    @SlashCommand(name = "punishment", subcommand = "history", description = "View punishment history for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentHistory(SlashCommandInteractionEvent event, @SlashOption Member member) {
        showHistory(event, member);
    }

    @SlashCommand(name = "punishment", subcommand = "search", description = "Search punishments with filters", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentSearch(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Filter by moderator", required = false) Member moderator,
        @SlashOption(description = "Filter by punishment type", required = false) String type
    ) {
        search(event, moderator, type);
    }

    @SlashCommand(name = "punishment", subcommand = "stats", description = "View punishment stats for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentStats(SlashCommandInteractionEvent event, @SlashOption Member member) {
        showStats(event, member);
    }

    @SlashComponentHandler(id = TYPE_PREFIX, patterns = {TYPE_PREFIX + "-*"})
    public void handleTypeSelect(StringSelectInteractionEvent event) {
        chooseType(event);
    }

    @SlashModalHandler(id = MODAL_PREFIX, patterns = {MODAL_PREFIX + "-*"})
    public void handleModalSubmit(ModalInteractionEvent event) {
        submit(event);
    }

    @SlashComponentHandler(id = DETAIL_PREFIX, patterns = {DETAIL_PREFIX + "-*"})
    public void handleDetailSelect(StringSelectInteractionEvent event) {
        selectDetail(event);
    }

    @SlashComponentHandler(id = NAV_PREFIX, patterns = {NAV_PREFIX + "-*"})
    public void handleNavigation(ButtonInteractionEvent event) {
        navigate(event);
    }

    @SlashComponentHandler(id = DELETE_PREFIX, patterns = {DELETE_PREFIX + "-*"})
    public void handleDelete(ButtonInteractionEvent event) {
        askToDelete(event);
    }

    @SlashComponentHandler(id = CONFIRM_YES_PREFIX, patterns = {CONFIRM_YES_PREFIX + "-*"})
    public void handleConfirmDelete(ButtonInteractionEvent event) {
        confirmDelete(event);
    }

    @SlashComponentHandler(id = CONFIRM_NO_PREFIX, patterns = {CONFIRM_NO_PREFIX + "-*"})
    public void handleCancelDelete(ButtonInteractionEvent event) {
        cancelDelete(event);
    }

    @SlashComponentHandler(id = BACK_PREFIX, patterns = {BACK_PREFIX + "-*"})
    public void handleBack(ButtonInteractionEvent event) {
        back(event);
    }

    void startCreate(IReplyCallback interaction, Member member) {
        CommandReplies.deferPrivately(interaction);
        String modId = interaction.getUser().getId();
        String targetId = member.getId();

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(TYPE.build(modId, targetId))
            .setPlaceholder("Select punishment type...")
            .setRequiredRange(1, 1);

        for (PunishmentType type : PunishmentType.values()) {
            selectBuilder.addOption(type.getDisplayName(), type.name(), type.getDescription());
        }

        interaction.getHook().editOriginal("Select the punishment type for <@" + targetId + ">:")
            .setComponents(ActionRow.of(selectBuilder.build()))
            .queue();
    }

    void showHistory(IReplyCallback interaction, Member member) {
        CommandReplies.deferPrivately(interaction);
        renderHistory(interaction.getHook(), interaction.getUser().getId(), member.getId(), 1, null);
    }

    void search(IReplyCallback interaction, @Nullable Member moderator, @Nullable String type) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        PunishmentType punishmentType = type != null ? PunishmentType.fromName(type) : null;
        if (type != null && punishmentType == null) {
            hook.editOriginal("Unknown punishment type: " + type).queue();
            return;
        }

        if (moderator == null && punishmentType == null) {
            hook.editOriginal("Please provide at least one filter (moderator or type).").queue();
            return;
        }

        List<Punishment> results = punishments.search(moderator != null ? moderator.getId() : null, punishmentType, ITEMS_PER_PAGE);
        if (results.isEmpty()) {
            hook.editOriginal("No punishments found matching the given filters.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Search Results")
            .setColor(Color.ORANGE)
            .setFooter("Showing " + results.size() + " result(s)");

        for (Punishment punishment : results) {
            String fieldValue = "**Target:** <@" + punishment.getTargetUserId() + ">\n" +
                "**Moderator:** <@" + punishment.getModeratorUserId() + ">\n" +
                "**Date:** " + DiscordTimestamp.toShortDateTime(punishment.getCreatedAt()) + "\n" +
                "**Reason:** " + StringUtils.truncate(punishment.getReason(), 100);
            embed.addField(punishment.getType().getDisplayName(), fieldValue, false);
        }

        hook.editOriginalEmbeds(embed.build()).queue();
    }

    void showStats(IReplyCallback interaction, Member member) {
        CommandReplies.deferPrivately(interaction);

        PunishmentStats stats = punishments.stats(member.getId());

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Stats for " + member.getEffectiveName())
            .setColor(Color.BLUE)
            .setThumbnail(member.getEffectiveAvatarUrl())
            .addField("Total", String.valueOf(stats.total()), true);

        stats.countsByType().forEach((type, count) ->
            embed.addField(type.getDisplayName(), String.valueOf(count), true));

        if (stats.total() == 0) {
            embed.setDescription("This user has no recorded punishments.");
        }

        interaction.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    void chooseType(StringSelectInteraction interaction) {
        Optional<List<String>> parts = TYPE.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty() || interaction.getValues().isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String targetId = parts.get().get(1);

        TextInput reasonInput = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Describe the reason for this punishment...")
            .setRequiredRange(1, 1024)
            .build();

        TextInput notesInput = TextInput.create("notes", "Notes (Optional)", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Any additional notes...")
            .setRequired(false)
            .setMaxLength(1024)
            .build();

        Modal modal = Modal.create(MODAL.build(modId, targetId, interaction.getValues().getFirst()), "Record Punishment")
            .addComponents(ActionRow.of(reasonInput), ActionRow.of(notesInput))
            .build();

        interaction.replyModal(modal).queue();
    }

    void submit(ModalInteraction interaction) {
        Optional<List<String>> parts = MODAL.parseFor(interaction, interaction.getModalId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String targetId = parts.get().get(1);

        InteractionHook hook = CommandReplies.deferPrivately(interaction);

        PunishmentType type = PunishmentType.fromName(parts.get().get(2));
        if (type == null) {
            hook.editOriginal(PanelId.OUTDATED).queue();
            return;
        }

        String reason = modalText(interaction, "reason");
        if (reason == null || reason.isBlank()) {
            hook.editOriginal("A reason is required.").queue();
            return;
        }

        CommandReplies.finish(punishments.record(targetId, modId, type, reason, modalText(interaction, "notes")), hook,
            "save a punishment for " + targetId + " by " + modId, "Failed to save punishment. Please try again.", punishment -> {
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Punishment Recorded")
                .setColor(Color.RED)
                .addField("Target", "<@" + targetId + ">", true)
                .addField("Type", type.getDisplayName(), true)
                .addField("Moderator", "<@" + modId + ">", true)
                .addField("Reason", reason, false)
                .setTimestamp(clock.instant());

            if (punishment.getNotes() != null) {
                embed.addField("Notes", punishment.getNotes(), false);
            }

            hook.editOriginalEmbeds(embed.build()).setComponents().queue();
            logChannel.send(embed.build());

            log.info("Punishment recorded: type={}, target={}, moderator={}, id={}",
                type.name(), targetId, modId, punishment.getPunishmentId());
        });
    }

    void selectDetail(StringSelectInteraction interaction) {
        Optional<List<String>> parts = DETAIL.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty() || interaction.getValues().isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderDetail(interaction.getHook(), parts.get().get(0), parts.get().get(1), interaction.getValues().getFirst());
    }

    void navigate(ButtonInteraction interaction) {
        Optional<List<String>> parts = NAV.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }

        Integer page = parsePage(parts.get().get(1));
        if (page == null) {
            interaction.reply(PanelId.OUTDATED).setEphemeral(true).queue();
            return;
        }

        int newPage = switch (parts.get().get(0)) {
            case "prev" -> page - 1;
            case "next" -> page + 1;
            default -> page;
        };

        interaction.deferEdit().queue();
        renderHistory(interaction.getHook(), parts.get().get(2), parts.get().get(3), newPage, null);
    }

    void askToDelete(ButtonInteraction interaction) {
        Optional<List<String>> parts = DELETE.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String punishmentId = parts.get().get(1);

        interaction.deferEdit().queue();

        ActionRow confirmRow = ActionRow.of(
            Button.danger(CONFIRM_YES.build(modId, punishmentId), "Confirm Delete"),
            Button.secondary(CONFIRM_NO.build(modId, punishmentId), "Cancel")
        );

        interaction.getHook().editOriginal("Are you sure you want to delete this punishment record? This cannot be undone.")
            .setEmbeds()
            .setComponents(confirmRow)
            .queue();
    }

    void confirmDelete(ButtonInteraction interaction) {
        Optional<List<String>> parts = CONFIRM_YES.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String punishmentId = parts.get().get(1);

        interaction.deferEdit().queue();
        InteractionHook hook = interaction.getHook();

        Optional<Punishment> found = punishments.find(punishmentId);
        if (found.isEmpty()) {
            hook.editOriginal("Punishment not found! It may have already been deleted.").setComponents().queue();
            return;
        }
        Punishment punishment = found.get();
        String targetId = punishment.getTargetUserId();

        CommandReplies.finish(punishments.delete(punishmentId), hook,
            "delete punishment " + punishmentId + " for " + modId, "Failed to delete punishment. Please try again.", ignored -> {
            logChannel.send(new EmbedBuilder()
                .setTitle("Punishment Deleted")
                .setColor(Color.GRAY)
                .addField("Target", "<@" + targetId + ">", true)
                .addField("Type", punishment.getType().getDisplayName(), true)
                .addField("Deleted By", "<@" + modId + ">", true)
                .setTimestamp(clock.instant())
                .build());

            log.info("Punishment deleted: id={}, target={}, deletedBy={}", punishmentId, targetId, modId);

            renderHistory(hook, modId, targetId, 1, "Successfully deleted punishment record.");
        });
    }

    void cancelDelete(ButtonInteraction interaction) {
        Optional<List<String>> parts = CONFIRM_NO.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderDetail(interaction.getHook(), parts.get().get(0), null, parts.get().get(1));
    }

    void back(ButtonInteraction interaction) {
        Optional<List<String>> parts = BACK.parseFor(interaction, interaction.getComponentId(), NOT_YOUR_PANEL);
        if (parts.isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderHistory(interaction.getHook(), parts.get().get(0), parts.get().get(1), 1, null);
    }

    @Nullable
    private static String modalText(ModalInteraction interaction, String inputId) {
        ModalMapping mapping = interaction.getValue(inputId);
        return mapping == null ? null : mapping.getAsString();
    }

    @Nullable
    private static Integer parsePage(String page) {
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void renderHistory(InteractionHook hook, String modId, String targetId, int page, @Nullable String message) {
        List<Punishment> history = punishments.history(targetId);
        String header = "**Punishment History for <@" + targetId + ">**\n\n" + (message != null ? message + "\n\n" : "");

        if (history.isEmpty()) {
            hook.editOriginal(header + "No punishments found for this user.").setComponents().setEmbeds().queue();
            return;
        }

        int totalPages = (history.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        int shownPage = Math.clamp(page, 1, totalPages);
        int firstIndex = (shownPage - 1) * ITEMS_PER_PAGE;
        List<Punishment> pageItems = history.subList(firstIndex, Math.min(firstIndex + ITEMS_PER_PAGE, history.size()));

        StringBuilder content = new StringBuilder(header)
            .append("**Total:** ").append(history.size()).append(" punishment(s)");
        if (totalPages > 1) {
            content.append(" (Page ").append(shownPage).append("/").append(totalPages).append(")");
        }
        content.append("\n\nSelect a punishment below to view details.");

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(DETAIL.build(modId, targetId))
            .setPlaceholder("Select a punishment to view...")
            .setRequiredRange(1, 1);

        for (int i = 0; i < pageItems.size(); i++) {
            Punishment punishment = pageItems.get(i);
            String label = "#" + (firstIndex + i + 1) + " - " + punishment.getType().getDisplayName();
            // toShortDate is a Discord format string, so the whole description is cut again to Discord's 100 character limit
            String description = StringUtils.truncate(
                StringUtils.truncate(punishment.getReason(), 50) + " (" + DiscordTimestamp.toShortDate(punishment.getCreatedAt()) + ")", 100);
            selectBuilder.addOption(label, punishment.getPunishmentId(), description);
        }

        List<ActionRow> actionRows = new ArrayList<>();
        actionRows.add(ActionRow.of(selectBuilder.build()));

        if (totalPages > 1) {
            actionRows.add(ActionRow.of(
                Button.secondary(NAV.build("prev", shownPage, modId, targetId), "Previous")
                    .withDisabled(shownPage <= 1),
                Button.secondary(NAV.build("next", shownPage, modId, targetId), "Next")
                    .withDisabled(shownPage >= totalPages)
            ));
        }

        hook.editOriginal(content.toString()).setComponents(actionRows).setEmbeds().queue();
    }

    /**
     * @param targetIdHint whose history to go back to when the punishment is gone, or null to just
     *                     say it's gone
     */
    private void renderDetail(InteractionHook hook, String modId, @Nullable String targetIdHint, String punishmentId) {
        Optional<Punishment> found = punishments.find(punishmentId);
        if (found.isEmpty()) {
            String content = "Punishment not found! It may have been deleted.";
            if (targetIdHint != null) {
                renderHistory(hook, modId, targetIdHint, 1, content);
            } else {
                hook.editOriginal(content).setComponents().setEmbeds().queue();
            }
            return;
        }

        Punishment punishment = found.get();
        String targetId = punishment.getTargetUserId();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Detail")
            .setColor(Color.RED)
            .addField("Target", "<@" + targetId + ">", true)
            .addField("Type", punishment.getType().getDisplayName(), true)
            .addField("Moderator", "<@" + punishment.getModeratorUserId() + ">", true)
            .addField("Date", DiscordTimestamp.toLongDateTime(punishment.getCreatedAt()) + " (" + DiscordTimestamp.toRelativeTimestamp(punishment.getCreatedAt()) + ")", false)
            .addField("Reason", punishment.getReason(), false)
            .setFooter("ID: " + punishmentId);

        if (punishment.getNotes() != null && !punishment.getNotes().isBlank()) {
            embed.addField("Notes", punishment.getNotes(), false);
        }

        ActionRow actionRow = ActionRow.of(
            Button.danger(DELETE.build(modId, punishmentId), "Delete"),
            Button.secondary(BACK.build(modId, targetId), "Back to List")
        );

        hook.editOriginal("").setEmbeds(embed.build()).setComponents(actionRow).queue();
    }
}
