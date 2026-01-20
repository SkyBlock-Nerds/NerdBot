package net.hypixel.nerdbot.app.ticket;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.hypixel.nerdbot.app.ticket.control.BuiltInTicketActions;
import net.hypixel.nerdbot.app.ticket.control.TicketAction;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketStatusTransition;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;

/**
 * Handles button clicks for ticket control panel buttons.
 * Supports both action buttons (ticket:action:*) and status transition buttons (ticket:status:*).
 */
@Slf4j
public class TicketButtonHandler {

    private static final String ERROR_MESSAGE = "An error occurred while processing your request.";

    /**
     * Handle a button click from a ticket control panel.
     */
    public static void handleButtonClick(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        // Route to appropriate handler based on button type
        if (componentId.startsWith("ticket:action:")) {
            handleActionButton(event, componentId.substring("ticket:action:".length()));
        } else if (componentId.startsWith("ticket:status:")) {
            handleStatusTransition(event, componentId.substring("ticket:status:".length()));
        } else {
            log.debug("Ignoring unrecognized ticket button: {}", componentId);
        }
    }

    /**
     * Handle built-in action buttons (claim, transfer, etc.).
     */
    private static void handleActionButton(ButtonInteractionEvent event, String actionId) {
        Ticket ticket = findTicketFromEvent(event);
        if (ticket == null) {
            return;
        }

        // Only staff can use action buttons
        if (!isStaff(event.getMember(), ticket)) {
            event.getHook().editOriginal("You do not have permission to use this button.").queue();
            return;
        }

        TicketAction action = switch (actionId) {
            case "claim" -> BuiltInTicketActions.CLAIM;
            case "close" -> BuiltInTicketActions.CLOSE;
            case "reopen" -> BuiltInTicketActions.REOPEN;
            default -> {
                log.warn("Unknown ticket action: {}", actionId);
                event.getHook().editOriginal("Unknown action.").queue();
                yield null;
            }
        };

        if (action == null) {
            return;
        }

        if (!action.isVisible(ticket) || !action.isEnabled(ticket)) {
            event.getHook().editOriginal("This action is not available for this ticket.").queue();
            return;
        }

        action.handle(ticket, event.getUser(), event);
        TicketService.getInstance().refreshControlPanel(ticket);

        log.info("Handled action {} for ticket {} by {}",
            actionId, ticket.getFormattedTicketId(), event.getUser().getId());
    }

    /**
     * Handle status transition buttons (legacy support).
     */
    private static void handleStatusTransition(ButtonInteractionEvent event, String targetStatusId) {
        Ticket ticket = findTicketFromEvent(event);
        if (ticket == null) {
            return;
        }

        // Only staff can use status transition buttons
        if (!isStaff(event.getMember(), ticket)) {
            event.getHook().editOriginal("You do not have permission to use this button.").queue();
            return;
        }

        performStatusTransition(event, ticket, targetStatusId);
    }

    /**
     * Handle status change from select menu.
     *
     * @param event          the select menu interaction event
     * @param targetStatusId the selected target status ID
     */
    public static void handleStatusSelect(StringSelectInteractionEvent event, String targetStatusId) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            event.getHook().editOriginal("This menu must be used inside a ticket channel.").queue();
            return;
        }

        TicketService service = TicketService.getInstance();
        Ticket ticket = service.getTicketRepository()
            .findByChannelId(channel.getId())
            .orElse(null);

        if (ticket == null) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return;
        }

        // Only staff can use status select menu
        if (!isStaff(event.getMember(), ticket)) {
            event.getHook().editOriginal("You do not have permission to change ticket status.").queue();
            return;
        }

        performStatusTransition(event, ticket, targetStatusId);
    }

    /**
     * Perform the actual status transition (shared logic for buttons and select menus).
     */
    private static void performStatusTransition(IReplyCallback event, Ticket ticket, String targetStatusId) {
        TicketService service = TicketService.getInstance();
        TicketConfig config = service.getConfig();
        User actor = event.getUser();

        // Validate the transition
        TicketStatus currentStatus = ticket.getStatus();
        TicketStatus targetStatus = TicketStatus.fromId(targetStatusId);

        if (currentStatus == targetStatus) {
            event.getHook().editOriginal("Ticket is already in this status.").queue();
            return;
        }

        if (!config.isTransitionAllowed(currentStatus, targetStatus)) {
            event.getHook().editOriginal("This status transition is not allowed.").queue();
            log.warn("Attempted invalid transition from {} to {} for ticket {} by {}",
                currentStatus, targetStatus, ticket.getFormattedTicketId(), actor.getId());
            return;
        }

        TicketStatusTransition transition = config.getTransition(currentStatus, targetStatus);

        // Check if transition requires claim
        if (transition != null && transition.isRequiresClaim() && !ticket.isClaimed()) {
            event.getHook().editOriginal("This ticket must be claimed before changing status.").queue();
            return;
        }

        try {
            // Auto-claim if configured
            if (transition != null && transition.isAutoClaimOnTransition() && !ticket.isClaimed()) {
                ticket.setClaimedById(actor.getId());
                log.info("Auto-claimed ticket {} for {} during status transition",
                    ticket.getFormattedTicketId(), actor.getId());
            }

            // Use appropriate method based on transition type
            if (targetStatus == TicketStatus.CLOSED) {
                // Closing - use closeTicket for full workflow (transcript, archive, etc.)
                service.closeTicket(ticket, actor, "Closed via status change");
                event.getHook().editOriginal("Ticket closed.").queue();
            } else if (currentStatus.isClosedState() && targetStatus == TicketStatus.OPEN) {
                // Reopening - use reopenTicket for full workflow (unarchive, restore permissions, etc.)
                service.reopenTicket(ticket, actor, "Reopened via status change");
                event.getHook().editOriginal("Ticket reopened.").queue();
            } else {
                // Regular status change
                service.updateStatus(ticket, targetStatus, actor);
                event.getHook().editOriginal("Status changed to **" + config.getStatusDisplayName(targetStatus) + "**.").queue();
                // Refresh control panel to show updated options
                service.refreshControlPanel(ticket);
            }
        } catch (Exception e) {
            log.error("Failed to transition ticket {} from {} to {}",
                ticket.getFormattedTicketId(), currentStatus, targetStatus, e);
            event.getHook().editOriginal(ERROR_MESSAGE).queue();
        }
    }

    /**
     * Find the ticket from the event context.
     */
    private static Ticket findTicketFromEvent(ButtonInteractionEvent event) {
        if (!(event.getChannel() instanceof TextChannel thread)) {
            log.debug("Button click not in a ticket channel: {}", event.getChannel().getId());
            event.getHook().editOriginal("This button must be used inside a ticket channel.").queue();
            return null;
        }

        TicketService service = TicketService.getInstance();
        Ticket ticket = service.getTicketRepository()
            .findByChannelId(thread.getId())
            .orElse(null);

        if (ticket == null) {
            event.getHook().editOriginal("This is not a ticket channel.").queue();
            return null;
        }

        return ticket;
    }

    /**
     * Check if the member is staff (has the ticket role OR moderator permissions).
     * Staff is determined by:
     * 1. Having the configured ticket role, OR
     * 2. Having BAN_MEMBERS or ADMINISTRATOR permission
     * <p>
     * Note: Staff who are also ticket owners CAN use controls (testing bypass).
     * Only non-staff ticket owners are blocked.
     *
     * @param member the guild member
     * @param ticket the ticket being interacted with
     *
     * @return true if the member is staff
     */
    private static boolean isStaff(Member member, Ticket ticket) {
        if (member == null) {
            return false;
        }

        // Check for moderator-level permissions (ADMINISTRATOR or BAN_MEMBERS)
        // This allows admins/mods to always manage tickets, even their own (for testing)
        if (member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.BAN_MEMBERS)) {
            return true;
        }

        TicketConfig config = TicketService.getInstance().getConfig();
        String ticketRoleId = config.getTicketRoleId();

        if (ticketRoleId == null || ticketRoleId.isEmpty()) {
            // No ticket role configured - block ticket owners
            return !member.getId().equals(ticket.getOwnerId());
        }

        // Check if user has the ticket role
        boolean hasTicketRole = member.getRoles().stream()
            .anyMatch(role -> role.getId().equals(ticketRoleId));

        if (hasTicketRole) {
            return true; // Has ticket role - allow even if they're the ticket owner
        }

        // Not staff - block ticket owners from managing their own tickets
        return false;
    }
}