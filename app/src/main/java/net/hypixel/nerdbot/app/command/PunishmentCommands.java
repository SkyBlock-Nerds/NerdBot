package net.hypixel.nerdbot.app.command;

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
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import net.hypixel.nerdbot.marmalade.storage.database.repository.PunishmentRepository;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PunishmentCommands {

    private static final int ITEMS_PER_PAGE = 10;

    private PunishmentRepository getPunishmentRepository() {
        return BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(PunishmentRepository.class);
    }

    @SlashCommand(name = "punishment", subcommand = "create", description = "Record a new punishment for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentCreate(SlashCommandInteractionEvent event, @SlashOption Member member) {
        event.deferReply(true).complete();
        String modId = event.getUser().getId();
        String targetId = member.getId();

        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create("punish-type-" + modId + "-" + targetId)
            .setPlaceholder("Select punishment type...")
            .setRequiredRange(1, 1);

        for (PunishmentType type : PunishmentType.values()) {
            selectBuilder.addOption(type.getDisplayName(), type.name(), type.getDescription());
        }

        event.getHook().editOriginal("Select the punishment type for <@" + targetId + ">:")
            .setComponents(ActionRow.of(selectBuilder.build()))
            .queue();
    }

    @SlashCommand(name = "punishment", subcommand = "history", description = "View punishment history for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentHistory(SlashCommandInteractionEvent event, @SlashOption Member member) {
        event.deferReply(true).complete();
        createHistoryPanel(event.getHook(), event.getUser().getId(), member.getId(), 1, null);
    }

    @SlashCommand(name = "punishment", subcommand = "search", description = "Search punishments with filters", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentSearch(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Filter by moderator", required = false) Member moderator,
        @SlashOption(description = "Filter by punishment type", required = false) String type
    ) {
        event.deferReply(true).complete();

        PunishmentRepository repo = getPunishmentRepository();
        List<Punishment> results;

        PunishmentType punishmentType = type != null ? PunishmentType.fromName(type) : null;

        if (moderator != null && punishmentType != null) {
            results = repo.findByModeratorUserId(moderator.getId()).stream()
                .filter(p -> p.getType() == punishmentType)
                .limit(ITEMS_PER_PAGE)
                .toList();
        } else if (moderator != null) {
            results = repo.findByModeratorUserId(moderator.getId()).stream()
                .limit(ITEMS_PER_PAGE)
                .toList();
        } else if (punishmentType != null) {
            results = repo.getAll().stream()
                .filter(p -> p.getType() == punishmentType)
                .limit(ITEMS_PER_PAGE)
                .toList();
        } else {
            event.getHook().editOriginal("Please provide at least one filter (moderator or type).").queue();
            return;
        }

        if (results.isEmpty()) {
            event.getHook().editOriginal("No punishments found matching the given filters.").queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Search Results")
            .setColor(Color.ORANGE)
            .setFooter("Showing " + results.size() + " result(s)");

        for (Punishment p : results) {
            String fieldValue = "**Target:** <@" + p.getTargetUserId() + ">\n" +
                "**Moderator:** <@" + p.getModeratorUserId() + ">\n" +
                "**Date:** " + DiscordTimestamp.toShortDateTime(p.getCreatedAt()) + "\n" +
                "**Reason:** " + StringUtils.truncate(p.getReason(), 100);
            embed.addField(p.getType().getDisplayName(), fieldValue, false);
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    @SlashCommand(name = "punishment", subcommand = "stats", description = "View punishment stats for a user", guildOnly = true, defaultMemberPermissions = {"BAN_MEMBERS"}, requiredPermissions = {"BAN_MEMBERS"})
    public void punishmentStats(SlashCommandInteractionEvent event, @SlashOption Member member) {
        event.deferReply(true).complete();

        PunishmentRepository repo = getPunishmentRepository();
        String targetId = member.getId();
        long total = repo.countByTargetUserId(targetId);

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Stats for " + member.getEffectiveName())
            .setColor(Color.BLUE)
            .setThumbnail(member.getEffectiveAvatarUrl())
            .addField("Total", String.valueOf(total), true);

        for (PunishmentType type : PunishmentType.values()) {
            long count = repo.countByTargetAndType(targetId, type);
            if (count > 0) {
                embed.addField(type.getDisplayName(), String.valueOf(count), true);
            }
        }

        if (total == 0) {
            embed.setDescription("This user has no recorded punishments.");
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    // ─── Type Select Handler ─────────────────────────────────────────

    @SlashComponentHandler(id = "punish-type", patterns = {"punish-type-*"})
    public void handleTypeSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String modId = parts[2];
        String targetId = parts[3];

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        String selectedType = event.getValues().get(0);

        TextInput reasonInput = TextInput.create("reason", "Reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Describe the reason for this punishment...")
            .setRequiredRange(1, 1024)
            .build();

        TextInput notesInput = TextInput.create("notes", "Notes (Optional)", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Any additional notes...")
            .setRequired(false)
            .setMaxLength(1024)
            .build();

        Modal modal = Modal.create("punish-modal-" + modId + "-" + targetId + "-" + selectedType, "Record Punishment")
            .addComponents(ActionRow.of(reasonInput), ActionRow.of(notesInput))
            .build();

        event.replyModal(modal).queue();
    }

    // ─── Modal Submit Handler ────────────────────────────────────────

    @SlashModalHandler(id = "punish-modal", patterns = {"punish-modal-*"})
    public void handleModalSubmit(ModalInteractionEvent event) {
        String[] parts = event.getModalId().split("-");
        String modId = parts[2];
        String targetId = parts[3];
        String typeName = parts[4];

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        PunishmentType type = PunishmentType.fromName(typeName);
        if (type == null) {
            event.getHook().editOriginal("Invalid punishment type!").queue();
            return;
        }

        String reason = event.getValue("reason").getAsString();
        String rawNotes = event.getValue("notes") != null ? event.getValue("notes").getAsString() : null;
        String notes = (rawNotes != null && !rawNotes.isBlank()) ? rawNotes : null;

        PunishmentRepository repo = getPunishmentRepository();
        Punishment punishment = new Punishment(targetId, modId, type, reason, notes);

        repo.cacheObject(punishment);
        repo.saveToDatabaseAsync(punishment).thenAccept(result -> {
            if (result != null && result.wasAcknowledged()) {
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Punishment Recorded")
                    .setColor(Color.RED)
                    .addField("Target", "<@" + targetId + ">", true)
                    .addField("Type", type.getDisplayName(), true)
                    .addField("Moderator", "<@" + modId + ">", true)
                    .addField("Reason", reason, false)
                    .setTimestamp(java.time.Instant.now());

                if (notes != null) {
                    embed.addField("Notes", notes, false);
                }

                event.getHook().editOriginalEmbeds(embed.build()).setComponents().queue();

                // Log to mod log channel
                ChannelCache.sendToLogChannel(embed.build());

                log.info("Punishment recorded: type={}, target={}, moderator={}, id={}",
                    type.name(), targetId, modId, punishment.getPunishmentId());
            } else {
                event.getHook().editOriginal("Failed to save punishment. Please try again.").queue();
                log.error("Failed to save punishment for target={}, moderator={}", targetId, modId);
            }
        });
    }

    // ─── History Panel Detail Select ─────────────────────────────────

    @SlashComponentHandler(id = "punish-detail", patterns = {"punish-detail-*"})
    public void handleDetailSelect(StringSelectInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String modId = parts[2];
        String targetId = parts[3];

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String punishmentId = event.getValues().get(0);
        showPunishmentDetail(event.getHook(), modId, targetId, punishmentId);
    }

    // ─── Navigation Button Handler ───────────────────────────────────

    @SlashComponentHandler(id = "punish-nav", patterns = {"punish-nav-*"})
    public void handleNavigation(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String action = parts[2]; // "prev" or "next"
        int page = Integer.parseInt(parts[3]);
        String modId = parts[4];
        String targetId = parts[5];

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        int newPage = switch (action) {
            case "prev" -> Math.max(1, page - 1);
            case "next" -> page + 1;
            default -> page;
        };

        createHistoryPanel(event.getHook(), modId, targetId, newPage, null);
    }

    // ─── Delete Button Handler ───────────────────────────────────────

    @SlashComponentHandler(id = "punish-delete", patterns = {"punish-delete-*"})
    public void handleDelete(ButtonInteractionEvent event) {
        // punish-delete-{modId}-{uuid parts...}
        String[] parts = event.getComponentId().split("-");
        String modId = parts[2];
        // UUID is parts[3] through parts[7]
        String punishmentId = String.join("-", parts[3], parts[4], parts[5], parts[6], parts[7]);

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        ActionRow confirmRow = ActionRow.of(
            Button.danger("punish-confirm-yes-" + modId + "-" + punishmentId, "Confirm Delete"),
            Button.secondary("punish-confirm-no-" + modId + "-" + punishmentId, "Cancel")
        );

        event.getHook().editOriginal("Are you sure you want to delete this punishment record? This cannot be undone.")
            .setEmbeds()
            .setComponents(confirmRow)
            .queue();
    }

    // ─── Confirm Delete Handler ──────────────────────────────────────

    @SlashComponentHandler(id = "punish-confirm-yes", patterns = {"punish-confirm-yes-*"})
    public void handleConfirmDelete(ButtonInteractionEvent event) {
        // punish-confirm-yes-{modId}-{uuid parts...}
        String[] parts = event.getComponentId().split("-");
        String modId = parts[3];
        // UUID is parts[4] through parts[8]
        String punishmentId = String.join("-", parts[4], parts[5], parts[6], parts[7], parts[8]);

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        PunishmentRepository repo = getPunishmentRepository();
        Punishment punishment = repo.findById(punishmentId);

        if (punishment == null) {
            event.getHook().editOriginal("Punishment not found! It may have already been deleted.")
                .setComponents()
                .queue();
            return;
        }

        String targetId = punishment.getTargetUserId();

        repo.deleteFromDatabaseAsync(punishmentId).thenAccept(result -> {
            if (result != null && result.wasAcknowledged()) {
                EmbedBuilder logEmbed = new EmbedBuilder()
                    .setTitle("Punishment Deleted")
                    .setColor(Color.GRAY)
                    .addField("Target", "<@" + targetId + ">", true)
                    .addField("Type", punishment.getType().getDisplayName(), true)
                    .addField("Deleted By", "<@" + modId + ">", true)
                    .setTimestamp(java.time.Instant.now());

                ChannelCache.sendToLogChannel(logEmbed.build());

                log.info("Punishment deleted: id={}, target={}, deletedBy={}", punishmentId, targetId, modId);

                createHistoryPanel(event.getHook(), modId, targetId, 1, "Successfully deleted punishment record.");
            } else {
                event.getHook().editOriginal("Failed to delete punishment. Please try again.")
                    .setComponents()
                    .queue();
            }
        });
    }

    // ─── Cancel Delete Handler ───────────────────────────────────────

    @SlashComponentHandler(id = "punish-confirm-no", patterns = {"punish-confirm-no-*"})
    public void handleCancelDelete(ButtonInteractionEvent event) {
        // punish-confirm-no-{modId}-{uuid parts...}
        String[] parts = event.getComponentId().split("-");
        String modId = parts[3];
        // UUID is parts[4] through parts[8]
        String punishmentId = String.join("-", parts[4], parts[5], parts[6], parts[7], parts[8]);

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        showPunishmentDetail(event.getHook(), modId, null, punishmentId);
    }

    // ─── Back to List Handler ────────────────────────────────────────

    @SlashComponentHandler(id = "punish-back", patterns = {"punish-back-*"})
    public void handleBack(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split("-");
        String modId = parts[2];
        String targetId = parts[3];

        if (!event.getUser().getId().equals(modId)) {
            event.reply("You can only use your own punishment interface!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        createHistoryPanel(event.getHook(), modId, targetId, 1, null);
    }

    // ─── Panel Builder ───────────────────────────────────────────────

    private void createHistoryPanel(InteractionHook hook, String modId, String targetId, int page, String message) {
        PunishmentRepository repo = getPunishmentRepository();
        List<Punishment> allPunishments = new ArrayList<>(repo.findByTargetUserId(targetId));

        if (allPunishments.isEmpty()) {
            String content = "**Punishment History for <@" + targetId + ">**\n\n" +
                (message != null ? message + "\n\n" : "") +
                "No punishments found for this user.";
            hook.editOriginal(content).setComponents().setEmbeds().queue();
            return;
        }

        int totalPages = (int) Math.ceil((double) allPunishments.size() / ITEMS_PER_PAGE);
        page = Math.min(Math.max(1, page), totalPages);

        List<Punishment> pagePunishments = allPunishments.stream()
            .skip((long) (page - 1) * ITEMS_PER_PAGE)
            .limit(ITEMS_PER_PAGE)
            .toList();

        StringBuilder content = new StringBuilder();
        content.append("**Punishment History for <@").append(targetId).append(">**\n\n");

        if (message != null) {
            content.append(message).append("\n\n");
        }

        content.append("**Total:** ").append(allPunishments.size()).append(" punishment(s)");
        if (totalPages > 1) {
            content.append(" (Page ").append(page).append("/").append(totalPages).append(")");
        }
        content.append("\n\nSelect a punishment below to view details.");

        List<ActionRow> actionRows = new ArrayList<>();

        // Select menu for punishment details
        StringSelectMenu.Builder selectBuilder = StringSelectMenu.create("punish-detail-" + modId + "-" + targetId)
            .setPlaceholder("Select a punishment to view...")
            .setRequiredRange(1, 1);

        for (int i = 0; i < pagePunishments.size(); i++) {
            Punishment p = pagePunishments.get(i);
            int globalIndex = (page - 1) * ITEMS_PER_PAGE + i + 1;
            String label = "#" + globalIndex + " - " + p.getType().getDisplayName();
            String description = StringUtils.truncate(p.getReason(), 50) + " (" + DiscordTimestamp.toShortDate(p.getCreatedAt()) + ")";
            // Discord requires descriptions <= 100 chars; toShortDate returns a Discord format string
            description = StringUtils.truncate(description, 100);
            selectBuilder.addOption(label, p.getPunishmentId(), description);
        }

        actionRows.add(ActionRow.of(selectBuilder.build()));

        // Navigation buttons
        List<Button> navButtons = new ArrayList<>();
        if (totalPages > 1) {
            navButtons.add(Button.secondary("punish-nav-prev-" + page + "-" + modId + "-" + targetId, "Previous")
                .withDisabled(page <= 1));
            navButtons.add(Button.secondary("punish-nav-next-" + page + "-" + modId + "-" + targetId, "Next")
                .withDisabled(page >= totalPages));
        }

        if (!navButtons.isEmpty()) {
            actionRows.add(ActionRow.of(navButtons));
        }

        hook.editOriginal(content.toString()).setComponents(actionRows).setEmbeds().queue();
    }

    // ─── Detail View ─────────────────────────────────────────────────

    private void showPunishmentDetail(InteractionHook hook, String modId, String targetIdHint, String punishmentId) {
        PunishmentRepository repo = getPunishmentRepository();
        Punishment punishment = repo.findById(punishmentId);

        if (punishment == null) {
            String content = "Punishment not found! It may have been deleted.";
            if (targetIdHint != null) {
                createHistoryPanel(hook, modId, targetIdHint, 1, content);
            } else {
                hook.editOriginal(content).setComponents().setEmbeds().queue();
            }
            return;
        }

        String targetId = punishment.getTargetUserId();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Punishment Detail")
            .setColor(Color.RED)
            .addField("Target", "<@" + targetId + ">", true)
            .addField("Type", punishment.getType().getDisplayName(), true)
            .addField("Moderator", "<@" + punishment.getModeratorUserId() + ">", true)
            .addField("Date", DiscordTimestamp.toLongDateTime(punishment.getCreatedAt()) + " (" + DiscordTimestamp.toRelativeTimestamp(punishment.getCreatedAt()) + ")", false)
            .addField("Reason", punishment.getReason(), false);

        if (punishment.getNotes() != null && !punishment.getNotes().isBlank()) {
            embed.addField("Notes", punishment.getNotes(), false);
        }

        embed.setFooter("ID: " + punishmentId);

        ActionRow actionRow = ActionRow.of(
            Button.danger("punish-delete-" + modId + "-" + punishmentId, "Delete"),
            Button.secondary("punish-back-" + modId + "-" + targetId, "Back to List")
        );

        hook.editOriginal("").setEmbeds(embed.build()).setComponents(actionRow).queue();
    }

}