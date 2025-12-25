package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketStatusConfig {

    /**
     * Unique identifier for the status (e.g., "open", "in_progress")
     */
    private String id;

    /**
     * Display name shown to users (e.g., "Open", "In Progress")
     */
    private String displayName;

    /**
     * Optional emoji for the forum tag (Unicode codepoint like "U+1F7E2" or custom emoji ID)
     */
    private String emoji;

    /**
     * Optional hex color code (e.g., "#00FF00") - for future use
     */
    private String color;

    /**
     * Whether this status represents a closed/resolved ticket
     */
    private boolean closedState;

    /**
     * Whether this is the default status for newly created tickets
     */
    private boolean defaultOpen;

    public TicketStatusConfig(String id, String displayName, String emoji, boolean closedState, boolean defaultOpen) {
        this.id = id;
        this.displayName = displayName;
        this.emoji = emoji;
        this.closedState = closedState;
        this.defaultOpen = defaultOpen;
    }
}