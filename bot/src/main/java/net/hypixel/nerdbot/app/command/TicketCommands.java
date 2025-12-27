package net.hypixel.nerdbot.app.command;

import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.ticket.TicketService;
import net.hypixel.nerdbot.core.csv.CSVData;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketStatusConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Slash commands that allow staff to manage ticket threads directly from Discord.
 * Includes utilities for closing, reopening, claiming, exporting, and reporting on tickets.
 */
@Slf4j
public class TicketCommands {

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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.isClosed()) {
            event.getHook().editOriginal("This ticket is already closed.").queue();
            return;
        }

        service.closeTicket(ticket, event.getUser(), reason);
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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        if (!ticket.isClosed()) {
            event.getHook().editOriginal("This ticket is not closed.").queue();
            return;
        }

        service.reopenTicket(ticket, event.getUser(), reason);
        event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " reopened successfully.").queue();
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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        String normalizedStatus = status.toLowerCase().replace(" ", "_");

        // Check if status exists in config
        if (config.getStatusById(normalizedStatus).isEmpty()) {
            List<String> validStatuses = config.getStatuses().stream()
                .map(TicketStatusConfig::getId)
                .toList();
            event.getHook().editOriginal("Invalid status: " + status + ". Valid options: " + String.join(", ", validStatuses)).queue();
            return;
        }

        // Don't allow setting to a closed status via this command
        if (config.isClosedStatus(normalizedStatus)) {
            event.getHook().editOriginal("Use `/ticket close` to close a ticket.").queue();
            return;
        }

        service.updateStatus(ticket, normalizedStatus, event.getUser());
        event.getHook().editOriginal("Status updated to: " + config.getStatusDisplayName(normalizedStatus)).queue();
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
            sb.append(config.getStatusDisplayName(ticket.getStatusId()));
            if (ticket.getThreadId() != null) {
                sb.append(" - <#").append(ticket.getThreadId()).append(">");
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
                " for " + member.getAsMention() + ": <#" + ticket.getThreadId() + ">").queue();
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

        if (!(event.getChannel() instanceof ThreadChannel thread)) {
            event.getHook().editOriginal("This command must be used in a ticket thread.").queue();
            return;
        }

        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            event.getHook().editOriginal("Could not connect to database!").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByThreadId(thread.getId());

        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket thread.").queue();
            return;
        }

        Ticket ticket = ticketOpt.get();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("**Ticket Information**\n\n");
        sb.append("**ID:** ").append(ticket.getFormattedTicketId()).append("\n");
        sb.append("**Owner:** <@").append(ticket.getOwnerId()).append(">\n");
        sb.append("**Category:** ").append(ticket.getCategoryId()).append("\n");
        sb.append("**Status:** ").append(config.getStatusDisplayName(ticket.getStatusId())).append("\n");
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
        List<Ticket> tickets = service.getTicketRepository().getAll().stream()
            .filter(t -> status == null || status.isEmpty() || t.getStatusId().equalsIgnoreCase(status))
            .filter(t -> category == null || category.isEmpty() || t.getCategoryId().equalsIgnoreCase(category))
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
            row.add(config.getStatusDisplayName(ticket.getStatusId()));
            row.add(ticket.getCategoryId());
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

        List<Ticket> tickets = service.getTicketRepository().getAll().stream()
            .filter(t -> status == null || status.isEmpty() || t.getStatusId().equalsIgnoreCase(status))
            .filter(t -> category == null || category.isEmpty() || t.getCategoryId().equalsIgnoreCase(category))
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
            sb.append(config.getStatusDisplayName(ticket.getStatusId()));
            sb.append(" (").append(ticket.getCategoryId()).append(")");
            if (ticket.getThreadId() != null) {
                sb.append(" - <#").append(ticket.getThreadId()).append(">");
            }
            sb.append("\n");
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
}
