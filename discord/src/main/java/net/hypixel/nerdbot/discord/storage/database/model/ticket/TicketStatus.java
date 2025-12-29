package net.hypixel.nerdbot.discord.storage.database.model.ticket;

import lombok.Getter;

/**
 * Fixed ticket status types. Display names and emojis are configurable via TicketConfig.
 */
@Getter
public enum TicketStatus {
    OPEN("open", false),
    IN_PROGRESS("in_progress", false),
    AWAITING_RESPONSE("awaiting_response", false),
    CLOSED("closed", true);

    /**
     * The ID used for database storage and config mapping.
     */
    private final String id;

    /**
     * Whether this status represents a closed/resolved ticket.
     */
    private final boolean closedState;

    private static final TicketStatus[] VALUES = values();

    TicketStatus(String id, boolean closedState) {
        this.id = id;
        this.closedState = closedState;
    }

    /**
     * Get a TicketStatus by its ID.
     *
     * @param id the status ID
     *
     * @return the matching status, or OPEN if not found
     */
    public static TicketStatus fromId(String id) {
        if (id == null) {
            return OPEN;
        }

        for (TicketStatus status : VALUES) {
            if (status.getId().equalsIgnoreCase(id)) {
                return status;
            }
        }

        return OPEN;
    }
}