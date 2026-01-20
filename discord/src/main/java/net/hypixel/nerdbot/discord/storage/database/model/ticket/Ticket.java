package net.hypixel.nerdbot.discord.storage.database.model.ticket;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent representation of a ticket channel, including metadata,
 * message history, and reminder bookkeeping stored in MongoDB.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Ticket {

    private static final int MAX_STORED_MESSAGES = 500;
    private int ticketNumber;
    private String ownerId;
    private String channelId;
    private TicketStatus status = TicketStatus.OPEN;
    private String ticketCategoryId;
    private String claimedById;
    private long createdAt;
    private long updatedAt;
    private long closedAt;
    private String closedById;
    private String closeReason;
    private List<TicketMessage> messages;
    private String buttonControllerMessageId;
    private String internalThreadId;
    private int totalMessageCount = 0;
    private boolean hasOverflowMessages = false;

    private int controlPanelRefreshAttempts = 0;

    private long lastReminderSent;
    private int lastReminderThresholdHours;

    /**
     * Timestamp of the first staff response, used for metrics.
     * Set once when the first staff member replies.
     */
    private long firstStaffResponseAt;

    /**
     * Custom field values submitted when the ticket was created.
     * Keys are field IDs from TicketTemplateField.
     * Uses LinkedHashMap to preserve insertion order.
     */
    private Map<String, TicketFieldValue> customFields = new LinkedHashMap<>();

    public Ticket(int ticketNumber, String ownerId) {
        this.ticketNumber = ticketNumber;
        this.ownerId = ownerId;
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
        totalMessageCount++;

        if (messages.size() > MAX_STORED_MESSAGES) {
            hasOverflowMessages = true;
        }

        updatedAt = System.currentTimeMillis();
    }

    /**
     * Get overflow messages that should be archived.
     * This removes them from the main messages list.
     *
     * @return list of overflow messages, or empty list if no overflow
     */
    public List<TicketMessage> getOverflowMessagesAndClear() {
        if (messages == null || messages.size() <= MAX_STORED_MESSAGES) {
            return new ArrayList<>();
        }

        List<TicketMessage> overflow = new ArrayList<>(messages.subList(0, messages.size() - MAX_STORED_MESSAGES));
        messages = new ArrayList<>(messages.subList(messages.size() - MAX_STORED_MESSAGES, messages.size()));
        hasOverflowMessages = true;

        return overflow;
    }

    /**
     * Get the status, defaulting to OPEN if not set.
     *
     * @return the ticket status (never null)
     */
    public TicketStatus getStatus() {
        return status != null ? status : TicketStatus.OPEN;
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

    /**
     * Record the first staff response time if not already set.
     *
     * @return true if this was the first staff response, false if already recorded
     */
    public boolean recordFirstStaffResponse() {
        if (firstStaffResponseAt <= 0) {
            firstStaffResponseAt = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    /**
     * Get the time in milliseconds from creation to first staff response.
     *
     * @return milliseconds to first response, or -1 if no staff response yet
     */
    public long getTimeToFirstResponseMs() {
        if (firstStaffResponseAt <= 0) {
            return -1;
        }

        return firstStaffResponseAt - createdAt;
    }

    /**
     * Add a custom field value to the ticket.
     *
     * @param fieldValue the field value to add
     */
    public void addCustomField(TicketFieldValue fieldValue) {
        if (customFields == null) {
            customFields = new LinkedHashMap<>();
        }

        customFields.put(fieldValue.getFieldId(), fieldValue);
    }

    /**
     * Add a custom field value to the ticket.
     *
     * @param fieldId the field ID
     * @param label   the field label
     * @param value   the submitted value
     */
    public void addCustomField(String fieldId, String label, String value) {
        addCustomField(new TicketFieldValue(fieldId, label, value));
    }

    /**
     * Check if this ticket has any custom fields.
     *
     * @return true if custom fields exist
     */
    public boolean hasCustomFields() {
        return customFields != null && !customFields.isEmpty();
    }

    /**
     * Get all custom field values in order.
     *
     * @return list of field values
     */
    public List<TicketFieldValue> getCustomFieldValues() {
        if (customFields == null) {
            return List.of();
        }

        return new ArrayList<>(customFields.values());
    }
}