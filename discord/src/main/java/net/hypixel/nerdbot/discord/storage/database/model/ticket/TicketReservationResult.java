package net.hypixel.nerdbot.discord.storage.database.model.ticket;

/**
 * Result of attempting to reserve a ticket slot.
 */
public record TicketReservationResult(
    boolean success,
    int ticketNumber,
    int currentOpenTickets,
    String errorMessage
) {
    public static TicketReservationResult success(int ticketNumber, int currentOpen) {
        return new TicketReservationResult(true, ticketNumber, currentOpen, null);
    }

    public static TicketReservationResult limitReached(int currentOpen, int maxAllowed) {
        return new TicketReservationResult(false, -1, currentOpen,
            "You have reached the maximum number of open tickets (" + maxAllowed + ")");
    }
}