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

import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.BACK;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.CONFIRM_NO;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.CONFIRM_YES;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.DELETE;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.DETAIL;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.MODAL;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.NAV;
import static net.hypixel.nerdbot.app.command.PunishmentComponentIds.TYPE;

/**
 * The {@code /punishment} commands and the panels they open. Each handler hands its event to a
 * method that takes the matching JDA interface, so tests can drive them with fakes.
 */
@Slf4j
public class PunishmentCommands {

    static final int ITEMS_PER_PAGE = 10;
    static final String NOT_YOUR_PANEL = "You can only use your own punishment interface!";
    static final String OUTDATED_PANEL = "This punishment panel is out of date. Run the command again.";

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

    @SlashComponentHandler(id = TYPE, patterns = {TYPE + "-*"})
    public void handleTypeSelect(StringSelectInteractionEvent event) {
        chooseType(event);
    }

    @SlashModalHandler(id = MODAL, patterns = {MODAL + "-*"})
    public void handleModalSubmit(ModalInteractionEvent event) {
        submit(event);
    }

    @SlashComponentHandler(id = DETAIL, patterns = {DETAIL + "-*"})
    public void handleDetailSelect(StringSelectInteractionEvent event) {
        selectDetail(event);
    }

    @SlashComponentHandler(id = NAV, patterns = {NAV + "-*"})
    public void handleNavigation(ButtonInteractionEvent event) {
        navigate(event);
    }

    @SlashComponentHandler(id = DELETE, patterns = {DELETE + "-*"})
    public void handleDelete(ButtonInteractionEvent event) {
        askToDelete(event);
    }

    @SlashComponentHandler(id = CONFIRM_YES, patterns = {CONFIRM_YES + "-*"})
    public void handleConfirmDelete(ButtonInteractionEvent event) {
        confirmDelete(event);
    }

    @SlashComponentHandler(id = CONFIRM_NO, patterns = {CONFIRM_NO + "-*"})
    public void handleCancelDelete(ButtonInteractionEvent event) {
        cancelDelete(event);
    }

    @SlashComponentHandler(id = BACK, patterns = {BACK + "-*"})
    public void handleBack(ButtonInteractionEvent event) {
        back(event);
    }

    void startCreate(IReplyCallback interaction, Member member) {
        interaction.deferReply(true).queue();
        String modId = interaction.getUser().getId();
        String targetId = member.getId();

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(PunishmentComponentIds.build(TYPE, modId, targetId))
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
        interaction.deferReply(true).queue();
        renderHistory(interaction.getHook(), interaction.getUser().getId(), member.getId(), 1, null);
    }

    void search(IReplyCallback interaction, @Nullable Member moderator, @Nullable String type) {
        interaction.deferReply(true).queue();
        InteractionHook hook = interaction.getHook();

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
        interaction.deferReply(true).queue();

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
        Optional<List<String>> parts = ownedParts(interaction, TYPE, interaction.getComponentId(), 2, 0);
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

        Modal modal = Modal.create(PunishmentComponentIds.build(MODAL, modId, targetId, interaction.getValues().getFirst()), "Record Punishment")
            .addComponents(ActionRow.of(reasonInput), ActionRow.of(notesInput))
            .build();

        interaction.replyModal(modal).queue();
    }

    void submit(ModalInteraction interaction) {
        Optional<List<String>> parts = ownedParts(interaction, MODAL, interaction.getModalId(), 3, 0);
        if (parts.isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String targetId = parts.get().get(1);

        interaction.deferReply(true).queue();
        InteractionHook hook = interaction.getHook();

        PunishmentType type = PunishmentType.fromName(parts.get().get(2));
        if (type == null) {
            hook.editOriginal(OUTDATED_PANEL).queue();
            return;
        }

        String reason = modalText(interaction, "reason");
        if (reason == null || reason.isBlank()) {
            hook.editOriginal("A reason is required.").queue();
            return;
        }

        punishments.record(targetId, modId, type, reason, modalText(interaction, "notes")).whenComplete((punishment, error) -> {
            if (error != null) {
                log.error("Failed to save punishment for target={}, moderator={}", targetId, modId, error);
                hook.editOriginal("Failed to save punishment. Please try again.").queue();
                return;
            }

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
        Optional<List<String>> parts = ownedParts(interaction, DETAIL, interaction.getComponentId(), 2, 0);
        if (parts.isEmpty() || interaction.getValues().isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderDetail(interaction.getHook(), parts.get().get(0), parts.get().get(1), interaction.getValues().getFirst());
    }

    void navigate(ButtonInteraction interaction) {
        Optional<List<String>> parts = ownedParts(interaction, NAV, interaction.getComponentId(), 4, 2);
        if (parts.isEmpty()) {
            return;
        }

        Integer page = parsePage(parts.get().get(1));
        if (page == null) {
            interaction.reply(OUTDATED_PANEL).setEphemeral(true).queue();
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
        Optional<List<String>> parts = ownedParts(interaction, DELETE, interaction.getComponentId(), 2, 0);
        if (parts.isEmpty()) {
            return;
        }
        String modId = parts.get().get(0);
        String punishmentId = parts.get().get(1);

        interaction.deferEdit().queue();

        ActionRow confirmRow = ActionRow.of(
            Button.danger(PunishmentComponentIds.build(CONFIRM_YES, modId, punishmentId), "Confirm Delete"),
            Button.secondary(PunishmentComponentIds.build(CONFIRM_NO, modId, punishmentId), "Cancel")
        );

        interaction.getHook().editOriginal("Are you sure you want to delete this punishment record? This cannot be undone.")
            .setEmbeds()
            .setComponents(confirmRow)
            .queue();
    }

    void confirmDelete(ButtonInteraction interaction) {
        Optional<List<String>> parts = ownedParts(interaction, CONFIRM_YES, interaction.getComponentId(), 2, 0);
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

        punishments.delete(punishmentId).whenComplete((ignored, error) -> {
            if (error != null) {
                log.error("Failed to delete punishment id={}, deletedBy={}", punishmentId, modId, error);
                hook.editOriginal("Failed to delete punishment. Please try again.").setComponents().queue();
                return;
            }

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
        Optional<List<String>> parts = ownedParts(interaction, CONFIRM_NO, interaction.getComponentId(), 2, 0);
        if (parts.isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderDetail(interaction.getHook(), parts.get().get(0), null, parts.get().get(1));
    }

    void back(ButtonInteraction interaction) {
        Optional<List<String>> parts = ownedParts(interaction, BACK, interaction.getComponentId(), 2, 0);
        if (parts.isEmpty()) {
            return;
        }

        interaction.deferEdit().queue();
        renderHistory(interaction.getHook(), parts.get().get(0), parts.get().get(1), 1, null);
    }

    /**
     * The parts of a panel id, or empty after telling the user why not: the id isn't one this class
     * made, or someone other than the moderator who opened the panel clicked it.
     */
    private static Optional<List<String>> ownedParts(IReplyCallback interaction, String prefix, String id, int count, int moderatorIndex) {
        Optional<List<String>> parts = PunishmentComponentIds.parse(prefix, id, count);
        if (parts.isEmpty()) {
            log.warn("Ignoring malformed punishment panel id '{}'", id);
            interaction.reply(OUTDATED_PANEL).setEphemeral(true).queue();
            return Optional.empty();
        }

        if (!interaction.getUser().getId().equals(parts.get().get(moderatorIndex))) {
            interaction.reply(NOT_YOUR_PANEL).setEphemeral(true).queue();
            return Optional.empty();
        }

        return parts;
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

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create(PunishmentComponentIds.build(DETAIL, modId, targetId))
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
                Button.secondary(PunishmentComponentIds.build(NAV, "prev", shownPage, modId, targetId), "Previous")
                    .withDisabled(shownPage <= 1),
                Button.secondary(PunishmentComponentIds.build(NAV, "next", shownPage, modId, targetId), "Next")
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
            Button.danger(PunishmentComponentIds.build(DELETE, modId, punishmentId), "Delete"),
            Button.secondary(PunishmentComponentIds.build(BACK, modId, targetId), "Back to List")
        );

        hook.editOriginal("").setEmbeds(embed.build()).setComponents(actionRow).queue();
    }
}
