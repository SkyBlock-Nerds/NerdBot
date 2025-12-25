package net.hypixel.nerdbot.discord.storage.database.model.ticket;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent representation of a ticket thread, including metadata,
 * message history, and reminder bookkeeping stored in MongoDB.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Ticket {

    private static final String DEFAULT_STATUS_ID = "open";

    private int ticketNumber;
    private String ownerId;
    private String threadId;
    private String forumChannelId;
    private String statusId;
    private String categoryId;
    private String claimedById;
    private long createdAt;
    private long updatedAt;
    private long closedAt;
    private String closedById;
    private String closeReason;
    private List<TicketMessage> messages;

    // Reminder tracking
    private long lastReminderSent;
    private int lastReminderThresholdHours;

    public Ticket(int ticketNumber, String ownerId) {
        this.ticketNumber = ticketNumber;
        this.ownerId = ownerId;
        this.statusId = DEFAULT_STATUS_ID;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.messages = new ArrayList<>();
    }

    public String getFormattedTicketId() {
        return String.format("#%04d", ticketNumber);
    }

    public void addMessage(TicketMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
        updatedAt = System.currentTimeMillis();
    }

    /**
     * Get the status ID, defaulting to "open" if not set
     *
     * @return the status ID (never null)
     */
    public String getStatusId() {
        return statusId != null ? statusId : DEFAULT_STATUS_ID;
    }

    /**
     * Check if the ticket is open (not closed)
     * Uses closedAt timestamp as the source of truth
     *
     * @return true if the ticket is open
     */
    public boolean isOpen() {
        return closedAt <= 0;
    }

    /**
     * Check if the ticket is closed
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closedAt > 0;
    }

    /**
     * Check if the ticket is claimed by a staff member
     *
     * @return true if claimed
     */
    public boolean isClaimed() {
        return claimedById != null && !claimedById.isEmpty();
    }

    /**
     * Reset reminder tracking - call when ticket status changes or receives a reply
     */
    public void resetReminderTracking() {
        this.lastReminderSent = 0;
        this.lastReminderThresholdHours = 0;
    }
}