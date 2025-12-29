package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.core.csv.CSVData;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.ChannelIndexStats;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Slash commands that allow staff to manage ticket channels directly from Discord.
 * Includes utilities for closing, reopening, claiming, exporting, and reporting on tickets.
 */
@Slf4j
public class TicketCommands {

    @SlashCommand(
        name = "ticket",
        subcommand = "setup",
        description = "Post the ticket creation panel in the current channel",
        guildOnly = true,
        requiredPermissions = {"ADMINISTRATOR"}
    )
    public void setupTicketPanel(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Title for the embed", required = false) String title,
        @SlashOption(description = "Description for the embed", required = false) String description) {

        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel channel)) {
            event.getHook().editOriginal("This command must be used in a text channel.").queue();
            return;
        }

        String embedTitle = title != null ? title : "Tickets";
        String embedDescription = description != null ? description : """
            Need help? Click the button below to create a ticket.

            Our team will assist you as soon as possible.
            """;

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(embedTitle)
            .setDescription(embedDescription)
            .setColor(0x5865F2);

        Button createButton = Button.primary("ticket:create", "Create Ticket")
            .withEmoji(Emoji.fromUnicode("\uD83C\uDFAB")); // 🎫

        channel.sendMessageEmbeds(embed.build())
            .setActionRow(createButton)
            .queue(
                success -> event.getHook().editOriginal("Ticket panel posted successfully.").queue(),
                error -> {
                    log.error("Failed to post ticket panel", error);
                    event.getHook().editOriginal("Failed to post ticket panel: " + error.getMessage()).queue();
                }
            );
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "close",
        description = "Close the current ticket",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void closeTicket(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Reason for closing", required = false) String reason) {

        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.isClosed()) {
            event.getHook().editOriginal("This ticket is already closed.").queue();
            return;
        }

        String effectiveReason = (reason == null || reason.isBlank()) ? "Closed by staff" : reason;
        service.closeTicket(ticket, event.getUser(), effectiveReason);
        event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " closed successfully.").queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "reopen",
        description = "Reopen a closed ticket",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void reopenTicket(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Reason for reopening", required = false) String reason) {

        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        if (!ticket.isClosed()) {
            event.getHook().editOriginal("This ticket is not closed.").queue();
            return;
        }

        try {
            service.reopenTicket(ticket, event.getUser(), reason);
            event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " reopened successfully.").queue();
        } catch (Exception exception) {
            reportCommandError(event, "reopen ticket " + ticket.getFormattedTicketId(), exception);
        }
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "claim",
        description = "Claim this ticket",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void claimTicket(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.isClosed()) {
            event.getHook().editOriginal("Cannot claim a closed ticket.").queue();
            return;
        }

        if (ticket.isClaimed()) {
            event.getHook().editOriginal("This ticket is already claimed by <@" + ticket.getClaimedById() + ">.").queue();
            return;
        }

        service.claimTicket(ticket, event.getUser());
        event.getHook().editOriginal("You have claimed ticket " + ticket.getFormattedTicketId() + ".").queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "transfer",
        description = "Transfer this ticket to another staff member",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void transferTicket(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Staff member to transfer the ticket to") Member newOwner) {

        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        // Check if the target user has the ticket role
        String ticketRoleId = config.getTicketRoleId();
        if (ticketRoleId != null && !ticketRoleId.isEmpty()) {
            boolean hasRole = newOwner.getRoles().stream()
                .anyMatch(r -> r.getId().equals(ticketRoleId));
            if (!hasRole) {
                event.getHook().editOriginal(newOwner.getAsMention() + " does not have the ticket role.").queue();
                return;
            }
        }

        Ticket ticket = ticketOpt.get();
        service.transferTicket(ticket, newOwner.getUser(), event.getUser());
        event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " transferred to " + newOwner.getAsMention() + ".").queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "status",
        description = "Change ticket status",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void setStatus(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "New status for the ticket", autocompleteId = "ticket-statuses") String status) {

        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        String normalizedStatus = status.toLowerCase().replace(" ", "_");

        // Parse status to enum
        TicketStatus newStatus = TicketStatus.fromId(normalizedStatus);

        // Verify this is a valid status (fromId defaults to OPEN for unknown values)
        if (!normalizedStatus.equalsIgnoreCase(newStatus.getId())) {
            String validStatuses = Arrays.stream(TicketStatus.values())
                .filter(s -> !s.isClosedState())
                .map(TicketStatus::getId)
                .collect(Collectors.joining(", "));
            event.getHook().editOriginal("Invalid status: " + status + ". Valid options: " + validStatuses).queue();
            return;
        }

        // Don't allow setting to a closed status via this command
        if (newStatus.isClosedState()) {
            event.getHook().editOriginal("Use `/ticket close` to close a ticket.").queue();
            return;
        }

        service.updateStatus(ticket, newStatus, event.getUser());
        event.getHook().editOriginal("Status updated to: " + config.getStatusDisplayName(newStatus)).queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "find",
        description = "Find tickets for a user",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void findTickets(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User to search for") Member member) {

        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        List<Ticket> tickets = service.getTicketRepository().findAllTicketsByUser(member.getId());

        if (tickets.isEmpty()) {
            event.getHook().editOriginal("No tickets found for " + member.getAsMention()).queue();
            return;
        }

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("**Tickets for ").append(member.getAsMention()).append(":**\n\n");

        for (Ticket ticket : tickets) {
            sb.append("- **").append(ticket.getFormattedTicketId()).append("** - ");
            sb.append(config.getStatusDisplayName(ticket.getStatus()));
            if (ticket.getChannelId() != null) {
                sb.append(" - <#").append(ticket.getChannelId()).append(">");
            }
            sb.append("\n");
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "new",
        description = "Create a ticket on behalf of a user",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void createTicketForUser(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User to create ticket for") Member member,
        @SlashOption(description = "Category (e.g., general, bug_report, appeal)", autocompleteId = "ticket-categories") String category,
        @SlashOption(description = "Description of the issue") String description) {

        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        boolean validCategory = config.getCategories().stream()
            .anyMatch(c -> c.getId().equalsIgnoreCase(category));

        if (!validCategory) {
            List<String> validCategories = config.getCategories().stream()
                .map(TicketConfig.TicketCategory::getId)
                .toList();
            event.getHook().editOriginal("Invalid category: " + category + ". Valid options: " + String.join(", ", validCategories)).queue();
            return;
        }

        try {
            Ticket ticket = TicketService.getInstance()
                .createTicket(member.getUser(), category, description);

            event.getHook().editOriginal("Created ticket " + ticket.getFormattedTicketId() +
                " for " + member.getAsMention() + ": <#" + ticket.getChannelId() + ">").queue();
        } catch (IllegalStateException e) {
            event.getHook().editOriginal("Failed to create ticket: " + e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Failed to create ticket for user {}", member.getId(), e);
            event.getHook().editOriginal("An error occurred while creating the ticket.").queue();
        }
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "info",
        description = "Get information about the current ticket",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void ticketInfo(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        if (!(event.getChannel() instanceof TextChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket channel.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("**Ticket Information**\n\n");
        sb.append("**ID:** ").append(ticket.getFormattedTicketId()).append("\n");
        sb.append("**Owner:** <@").append(ticket.getOwnerId()).append(">\n");
        sb.append("**Category:** ").append(config.getCategoryDisplayName(ticket.getTicketCategoryId())).append("\n");
        sb.append("**Status:** ").append(config.getStatusDisplayName(ticket.getStatus())).append("\n");
        sb.append("**Created:** <t:").append(ticket.getCreatedAt() / 1_000).append(":F>\n");

        if (ticket.isClaimed()) {
            sb.append("**Claimed By:** <@").append(ticket.getClaimedById()).append(">\n");
        }

        if (ticket.getMessages() != null) {
            sb.append("**Messages:** ").append(ticket.getMessages().size()).append("\n");
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "export",
        description = "Export tickets to CSV",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void exportTickets(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Filter by status (leave empty for all)", required = false, autocompleteId = "ticket-statuses") String status,
        @SlashOption(description = "Filter by category (leave empty for all)", required = false, autocompleteId = "ticket-categories") String category,
        @SlashOption(description = "From date (YYYY-MM-DD)", required = false) String fromDate,
        @SlashOption(description = "To date (YYYY-MM-DD)", required = false) String toDate) {

        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        // Parse date range
        long fromTimestamp = 0;
        long toTimestamp = Long.MAX_VALUE;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (fromDate != null && !fromDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(fromDate, formatter);
                fromTimestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                event.getHook().editOriginal("Invalid from date format. Use YYYY-MM-DD.").queue();
                return;
            }
        }

        if (toDate != null && !toDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(toDate, formatter);
                toTimestamp = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                event.getHook().editOriginal("Invalid to date format. Use YYYY-MM-DD.").queue();
                return;
            }
        }

        // Build filtered ticket list
        final long finalFromTimestamp = fromTimestamp;
        final long finalToTimestamp = toTimestamp;
        final TicketStatus filterStatus = status != null && !status.isEmpty() ? TicketStatus.fromId(status) : null;
        List<Ticket> tickets = service.getTicketRepository().getAll().stream()
            .filter(t -> filterStatus == null || t.getStatus() == filterStatus)
            .filter(t -> category == null || category.isEmpty() || t.getTicketCategoryId().equalsIgnoreCase(category))
            .filter(t -> t.getCreatedAt() >= finalFromTimestamp && t.getCreatedAt() <= finalToTimestamp)
            .toList();

        if (tickets.isEmpty()) {
            event.getHook().editOriginal("No tickets found matching the criteria.").queue();
            return;
        }

        // Create CSV
        CSVData csv = new CSVData(List.of(
            "Ticket#", "Owner ID", "Status", "Category", "Created", "Closed", "Claimed By", "Messages"
        ));

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Ticket ticket : tickets) {
            List<String> row = new ArrayList<>();
            row.add(ticket.getFormattedTicketId());
            row.add(ticket.getOwnerId());
            row.add(config.getStatusDisplayName(ticket.getStatus()));
            row.add(config.getCategoryDisplayName(ticket.getTicketCategoryId()));
            row.add(formatTimestamp(ticket.getCreatedAt(), outputFormatter));
            row.add(ticket.getClosedAt() > 0 ? formatTimestamp(ticket.getClosedAt(), outputFormatter) : "");
            row.add(ticket.getClaimedById() != null ? ticket.getClaimedById() : "");
            row.add(String.valueOf(ticket.getMessages() != null ? ticket.getMessages().size() : 0));
            csv.addRow(row);
        }

        String csvContent = csv.toCSV();
        String filename = "tickets-export-" + LocalDate.now().format(formatter) + ".csv";

        event.getHook().editOriginal("Exported " + tickets.size() + " tickets:")
            .setFiles(FileUpload.fromData(csvContent.getBytes(StandardCharsets.UTF_8), filename))
            .queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "search",
        description = "Search tickets",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void searchTickets(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Keyword to search for in messages", required = false) String keyword,
        @SlashOption(description = "Filter by status", required = false, autocompleteId = "ticket-statuses") String status,
        @SlashOption(description = "Filter by category", required = false, autocompleteId = "ticket-categories") String category,
        @SlashOption(description = "Filter by user", required = false) Member user,
        @SlashOption(description = "From date (YYYY-MM-DD)", required = false) String fromDate,
        @SlashOption(description = "To date (YYYY-MM-DD)", required = false) String toDate) {

        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        // Parse date range
        long fromTimestamp = 0;
        long toTimestamp = Long.MAX_VALUE;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (fromDate != null && !fromDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(fromDate, formatter);
                fromTimestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                event.getHook().editOriginal("Invalid from date format. Use YYYY-MM-DD.").queue();
                return;
            }
        }

        if (toDate != null && !toDate.isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(toDate, formatter);
                toTimestamp = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                event.getHook().editOriginal("Invalid to date format. Use YYYY-MM-DD.").queue();
                return;
            }
        }

        // Build filtered ticket list
        final long finalFromTimestamp = fromTimestamp;
        final long finalToTimestamp = toTimestamp;
        final String userId = user != null ? user.getId() : null;
        final TicketStatus filterStatus = status != null && !status.isEmpty() ? TicketStatus.fromId(status) : null;

        List<Ticket> tickets = service.getTicketRepository().getAll().stream()
            .filter(t -> filterStatus == null || t.getStatus() == filterStatus)
            .filter(t -> category == null || category.isEmpty() || t.getTicketCategoryId().equalsIgnoreCase(category))
            .filter(t -> userId == null || t.getOwnerId().equals(userId))
            .filter(t -> t.getCreatedAt() >= finalFromTimestamp && t.getCreatedAt() <= finalToTimestamp)
            .filter(t -> {
                if (keyword == null || keyword.isEmpty()) {
                    return true;
                }
                String lowerKeyword = keyword.toLowerCase();
                return t.getMessages() != null && t.getMessages().stream()
                    .anyMatch(m -> m.getContent().toLowerCase().contains(lowerKeyword));
            })
            .limit(25)  // Limit results
            .toList();

        if (tickets.isEmpty()) {
            event.getHook().editOriginal("No tickets found matching the criteria.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Found ").append(tickets.size()).append(" ticket(s):**\n\n");

        for (Ticket ticket : tickets) {
            sb.append("- **").append(ticket.getFormattedTicketId()).append("** - ");
            sb.append(config.getStatusDisplayName(ticket.getStatus()));
            sb.append(" (").append(config.getCategoryDisplayName(ticket.getTicketCategoryId())).append(")");
            if (ticket.getChannelId() != null) {
                sb.append(" - <#").append(ticket.getChannelId()).append(">");
            }
            sb.append("\n");
        }

        if (sb.length() > 2_000) {
            sb.setLength(1_997);
            sb.append("...");
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "deleteall",
        description = "[DEBUG] Delete ALL tickets, ticket channels, and reset counter",
        guildOnly = true,
        requiredPermissions = {"ADMINISTRATOR"}
    )
    public void deleteAllTickets(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Type 'CONFIRM' to proceed", required = true) String confirm) {

        event.deferReply(true).complete();

        if (!confirm.equals("CONFIRM")) {
            event.getHook().editOriginal("You must type `CONFIRM` (case-sensitive) to delete all tickets.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        try {
            TicketService service = TicketService.getInstance();
            String result = service.deleteAllTickets();
            event.getHook().editOriginal("**Deletion complete:**\n" + result).queue();
        } catch (Exception e) {
            reportCommandError(event, "delete all tickets", e);
        }
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "stats",
        description = "View ticket statistics",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void ticketStats(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Time period: day, week, month, all (default: month)", required = false) String period) {

        event.deferReply(true).complete();

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        // Determine time range
        long now = System.currentTimeMillis();
        long fromTimestamp = 0;
        String periodLabel;

        if (period != null) {
            switch (period.toLowerCase()) {
                case "day" -> {
                    fromTimestamp = now - (24 * 60 * 60 * 1000L);
                    periodLabel = "Last 24 Hours";
                }
                case "week" -> {
                    fromTimestamp = now - (7 * 24 * 60 * 60 * 1000L);
                    periodLabel = "Last 7 Days";
                }
                case "month" -> {
                    fromTimestamp = now - (30L * 24 * 60 * 60 * 1000L);
                    periodLabel = "Last 30 Days";
                }
                case "all" -> periodLabel = "All Time";
                default -> {
                    event.getHook().editOriginal("Invalid period. Use: day, week, month, or all").queue();
                    return;
                }
            }
        } else {
            // Default to month
            fromTimestamp = now - (30L * 24 * 60 * 60 * 1000L);
            periodLabel = "Last 30 Days";
        }

        final long finalFromTimestamp = fromTimestamp;
        List<Ticket> allTickets = service.getTicketRepository().getAllDocuments();
        List<Ticket> periodTickets = allTickets.stream()
            .filter(t -> t.getCreatedAt() >= finalFromTimestamp)
            .toList();

        // Calculate statistics
        int totalTickets = periodTickets.size();
        int openTickets = (int) periodTickets.stream().filter(t -> !t.isClosed()).count();
        int closedTickets = (int) periodTickets.stream().filter(Ticket::isClosed).count();
        int claimedTickets = (int) periodTickets.stream().filter(Ticket::isClaimed).count();

        // Status breakdown
        StringBuilder statusBreakdown = new StringBuilder();
        for (TicketStatus status : TicketStatus.values()) {
            long count = periodTickets.stream().filter(t -> t.getStatus() == status).count();
            if (count > 0) {
                statusBreakdown.append("  ").append(config.getStatusDisplayName(status))
                    .append(": ").append(count).append("\n");
            }
        }

        // Category breakdown
        StringBuilder categoryBreakdown = new StringBuilder();
        for (TicketConfig.TicketCategory cat : config.getCategories()) {
            long count = periodTickets.stream()
                .filter(t -> cat.getId().equalsIgnoreCase(t.getTicketCategoryId()))
                .count();
            if (count > 0) {
                categoryBreakdown.append("  ").append(cat.getDisplayName())
                    .append(": ").append(count).append("\n");
            }
        }

        // Calculate average response time (time to first staff message)
        long totalResponseTime = 0;
        int ticketsWithResponse = 0;
        for (Ticket ticket : periodTickets) {
            if (ticket.getMessages() != null && ticket.getMessages().size() > 1) {
                // Find first staff message
                for (int i = 1; i < ticket.getMessages().size(); i++) {
                    if (ticket.getMessages().get(i).isStaff()) {
                        long responseTime = ticket.getMessages().get(i).getTimestamp() - ticket.getCreatedAt();
                        totalResponseTime += responseTime;
                        ticketsWithResponse++;
                        break;
                    }
                }
            }
        }

        String avgResponseTime = "N/A";
        if (ticketsWithResponse > 0) {
            long avgMs = totalResponseTime / ticketsWithResponse;
            avgResponseTime = formatDuration(avgMs);
        }

        // Calculate average resolution time (for closed tickets)
        long totalResolutionTime = 0;
        int resolvedTickets = 0;
        for (Ticket ticket : periodTickets) {
            if (ticket.isClosed() && ticket.getClosedAt() > 0) {
                long resolutionTime = ticket.getClosedAt() - ticket.getCreatedAt();
                totalResolutionTime += resolutionTime;
                resolvedTickets++;
            }
        }

        String avgResolutionTime = "N/A";
        if (resolvedTickets > 0) {
            long avgMs = totalResolutionTime / resolvedTickets;
            avgResolutionTime = formatDuration(avgMs);
        }

        // Top staff (by claims)
        StringBuilder topStaff = new StringBuilder();
        periodTickets.stream()
            .filter(Ticket::isClaimed)
            .collect(Collectors.groupingBy(Ticket::getClaimedById, Collectors.counting()))
            .entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(5)
            .forEach(entry -> topStaff.append("  <@").append(entry.getKey()).append(">: ")
                .append(entry.getValue()).append(" tickets\n"));

        StringBuilder sb = new StringBuilder();
        sb.append("**Ticket Statistics - ").append(periodLabel).append("**\n\n");

        sb.append("**Overview:**\n");
        sb.append("  Total Tickets: ").append(totalTickets).append("\n");
        sb.append("  Open: ").append(openTickets).append("\n");
        sb.append("  Closed: ").append(closedTickets).append("\n");
        sb.append("  Claimed: ").append(claimedTickets).append("\n\n");

        sb.append("**Response Times:**\n");
        sb.append("  Avg First Response: ").append(avgResponseTime).append("\n");
        sb.append("  Avg Resolution: ").append(avgResolutionTime).append("\n\n");

        if (!statusBreakdown.isEmpty()) {
            sb.append("**By Status:**\n").append(statusBreakdown).append("\n");
        }

        if (!categoryBreakdown.isEmpty()) {
            sb.append("**By Category:**\n").append(categoryBreakdown).append("\n");
        }

        if (!topStaff.isEmpty()) {
            sb.append("**Top Staff (by claims):**\n").append(topStaff);
        }

        // Thread index stats
        ChannelIndexStats indexStats = service.getChannelIndexStats();
        sb.append("\n**System:**\n");
        sb.append("  Channel Index: ").append(indexStats.indexSize()).append(" entries");
        if (!indexStats.isInSync()) {
            sb.append(" (").append(indexStats.getSyncDifference()).append(" out of sync)");
        }
        sb.append("\n");
        sb.append("  Cache: ").append(indexStats.openTicketCacheSize()).append(" open, ")
            .append(indexStats.closedTicketCacheSize()).append(" closed\n");

        event.getHook().editOriginal(sb.toString()).queue();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return seconds + "s";
        }
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "blacklist-add",
        description = "Add a user to the ticket blacklist",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void blacklistAdd(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User to blacklist") Member member,
        @SlashOption(description = "Reason for blacklisting", required = false) String reason) {

        event.deferReply(true).complete();

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        if (config.isUserBlacklisted(member.getId())) {
            event.getHook().editOriginal(member.getAsMention() + " is already blacklisted.").queue();
            return;
        }

        config.addToBlacklist(member.getId());
        DiscordBotEnvironment.getBot().writeConfig(DiscordBotEnvironment.getBot().getConfig());

        String message = "Added " + member.getAsMention() + " to the ticket blacklist.";
        if (reason != null && !reason.isBlank()) {
            message += " Reason: " + reason;
        }
        log.info("User {} blacklisted from tickets by {}. Reason: {}",
            member.getId(), event.getUser().getId(), reason != null ? reason : "No reason provided");
        event.getHook().editOriginal(message).queue();
    }

    @SlashCommand(
        name = "ticket",
        subcommand = "blacklist-remove",
        description = "Remove a user from the ticket blacklist",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void blacklistRemove(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User to remove from blacklist") Member member) {

        event.deferReply(true).complete();

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        if (!config.isUserBlacklisted(member.getId())) {
            event.getHook().editOriginal(member.getAsMention() + " is not blacklisted.").queue();
            return;
        }

        config.removeFromBlacklist(member.getId());
        DiscordBotEnvironment.getBot().writeConfig(DiscordBotEnvironment.getBot().getConfig());

        log.info("User {} removed from ticket blacklist by {}", member.getId(), event.getUser().getId());
        event.getHook().editOriginal("Removed " + member.getAsMention() + " from the ticket blacklist.").queue();
    }

    @SlashCommand(
        name = "ticket",
        group = "blacklist",
        subcommand = "list",
        description = "List all blacklisted users",
        guildOnly = true,
        requiredPermissions = {"BAN_MEMBERS"}
    )
    public void blacklistList(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        if (config.getBlacklistedUserIds() == null || config.getBlacklistedUserIds().isEmpty()) {
            event.getHook().editOriginal("No users are currently blacklisted.").queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Blacklisted Users (").append(config.getBlacklistedUserIds().size()).append("):**\n\n");

        for (String userId : config.getBlacklistedUserIds()) {
            sb.append("- <@").append(userId).append("> (`").append(userId).append("`)\n");
        }

        if (sb.length() > 2_000) {
            sb.setLength(1_997);
            sb.append("...");
        }

        event.getHook().editOriginal(sb.toString()).queue();
    }

    @SlashAutocompleteHandler(id = "ticket-statuses")
    public List<Command.Choice> autocompleteStatuses(CommandAutoCompleteInteractionEvent event) {
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        String currentInput = event.getFocusedOption().getValue().toLowerCase();

        return config.getStatuses().stream()
            .filter(s -> s.getId().toLowerCase().contains(currentInput)
                || s.getDisplayName().toLowerCase().contains(currentInput))
            .map(s -> new Command.Choice(s.getDisplayName(), s.getId()))
            .limit(25)
            .collect(Collectors.toList());
    }

    @SlashAutocompleteHandler(id = "ticket-categories")
    public List<Command.Choice> autocompleteCategories(CommandAutoCompleteInteractionEvent event) {
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        String currentInput = event.getFocusedOption().getValue().toLowerCase();

        return config.getCategories().stream()
            .filter(c -> c.getId().toLowerCase().contains(currentInput)
                || c.getDisplayName().toLowerCase().contains(currentInput))
            .map(c -> new Command.Choice(c.getDisplayName(), c.getId()))
            .limit(25)
            .collect(Collectors.toList());
    }

    private String formatTimestamp(long epochMillis, DateTimeFormatter formatter) {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter);
    }

    private void reportCommandError(SlashCommandInteractionEvent event, String action, Exception exception) {
        log.error("Failed to {}: {}", action, exception.getMessage(), exception);
        String reason = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
        event.getHook().editOriginal("Failed to " + action + ": " + reason).queue();
    }
}