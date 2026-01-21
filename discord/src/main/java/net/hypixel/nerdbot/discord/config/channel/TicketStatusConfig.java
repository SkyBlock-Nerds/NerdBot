package net.hypixel.nerdbot.discord.config.channel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable display settings for a ticket status.
 * The status types are fixed by {@link TicketStatus}, but display names, emojis,
 * and allowed transitions are configurable.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TicketStatusConfig {

    /**
     * Status ID matching {@link TicketStatus#getId()} (e.g., "open", "in_progress")
     */
    private String id;

    /**
     * Display name shown to users (e.g., "Open", "In Progress")
     */
    private String displayName;

    /**
     * Emoji shown in buttons and messages (Unicode like "🟢" or custom emoji ID)
     */
    private String emoji;

    /**
     * List of allowed transitions from this status.
     * If empty, default transitions will be used based on the status type.
     */
    private List<TicketStatusTransition> allowedTransitions = new ArrayList<>();

    /**
     * Constructor for basic status config without transitions.
     */
    public TicketStatusConfig(String id, String displayName, String emoji) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
    }

    /**
     * Constructor with transitions.
     */
    public TicketStatusConfig(String id, String displayName, String emoji, List<TicketStatusTransition> allowedTransitions) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.allowedTransitions = allowedTransitions != null ? allowedTransitions : new ArrayList<>();
    }

    /**
     * Check if a transition to the target status is allowed.
     *
     * @param targetStatusId the target status ID
     *
     * @return true if the transition is allowed
     */
    public boolean canTransitionTo(String targetStatusId) {
        if (targetStatusId == null) {
            return false;
        }
        return allowedTransitions.stream()
            .anyMatch(t -> t.getTargetStatusId().equalsIgnoreCase(targetStatusId));
    }

    /**
     * Get the transition config for a target status.
     *
     * @param targetStatusId the target status ID
     *
     * @return the transition config, or null if not found
     */
    public TicketStatusTransition getTransition(String targetStatusId) {
        if (targetStatusId == null) {
            return null;
        }
        return allowedTransitions.stream()
            .filter(t -> t.getTargetStatusId().equalsIgnoreCase(targetStatusId))
            .findFirst()
            .orElse(null);
    }
}