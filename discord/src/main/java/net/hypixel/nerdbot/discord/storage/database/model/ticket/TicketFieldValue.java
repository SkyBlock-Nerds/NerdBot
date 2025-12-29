package net.hypixel.nerdbot.discord.storage.database.model.ticket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Stores a custom field value submitted when creating a ticket.
 * Links to the field definition in TicketTemplateField via fieldId.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketFieldValue {

    /**
     * The field ID from TicketTemplateField
     */
    private String fieldId;

    /**
     * The label of the field (copied at submission time for display)
     */
    private String label;

    /**
     * The value submitted by the user
     */
    private String value;

    /**
     * Timestamp when this field was submitted
     */
    private long submittedAt;

    /**
     * Convenience constructor without timestamp (uses current time)
     */
    public TicketFieldValue(String fieldId, String label, String value) {
        this.fieldId = fieldId;
        this.label = label;
        this.value = value;
        this.submittedAt = System.currentTimeMillis();
    }
}