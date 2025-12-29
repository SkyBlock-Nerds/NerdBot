package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Defines an allowed transition from one ticket status to another.
 * Used in {@link TicketStatusConfig} to configure valid status workflows.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketStatusTransition {

    /**
     * The target status ID to transition to (e.g., "in_progress", "closed").
     * Must match a valid {@link net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus} ID.
     */
    private String targetStatusId;

    /**
     * Label shown on the button for this transition (e.g., "Start Working", "Close").
     * If null or empty, the target status display name will be used.
     */
    private String buttonLabel;

    /**
     * Whether this transition requires the ticket to be claimed first.
     * Default is false.
     */
    private boolean requiresClaim = false;

    /**
     * Whether this transition should automatically claim the ticket for the actor.
     * Default is false. Only applies if the ticket is not already claimed.
     */
    private boolean autoClaimOnTransition = false;

    /**
     * Convenience constructor with button label.
     *
     * @param targetStatusId the target status ID
     * @param buttonLabel    the button label
     */
    public TicketStatusTransition(String targetStatusId, String buttonLabel) {
        this.targetStatusId = targetStatusId;
        this.buttonLabel = buttonLabel;
    }
}