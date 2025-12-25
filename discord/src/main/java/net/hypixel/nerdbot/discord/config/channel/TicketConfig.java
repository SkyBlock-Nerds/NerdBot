package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
@ToString
public class TicketConfig {

    /**
     * The ID of the forum channel where tickets will be created
     */
    private String forumChannelId = "";

    /**
     * The ID of the role to ping for new tickets
     */
    private String ticketRoleId = "";

    /**
     * Optional webhook ID for mirroring ticket updates
     */
    private String webhookId = "";

    /**
     * Available ticket categories. Initializes with three default
     * categories
     */
    private List<TicketCategory> categories = new ArrayList<>(List.of(
        new TicketCategory("general", "General", "General support questions"),
        new TicketCategory("bug_report", "Bug Report", "Report a bug or issue"),
        new TicketCategory("appeal", "Appeal", "Appeal a moderation action")
    ));

    /**
     * Configurable ticket statuses with emoji and display settings.
     * Initializes with four default states
     */
    private List<TicketStatusConfig> statuses = new ArrayList<>(List.of(
        new TicketStatusConfig("open", "Open", "\uD83D\uDFE2", false, true),
        new TicketStatusConfig("in_progress", "In Progress", "\uD83D\uDFE1", false, false),
        new TicketStatusConfig("awaiting_response", "Awaiting Response", "\uD83D\uDFE0", false, false),
        new TicketStatusConfig("closed", "Closed", "\uD83D\uDD34", true, false)
    ));

    // ==================== Auto-Status Configuration ====================

    /**
     * Status to automatically set when a user replies to a ticket (null to disable).
     * Default is open
     */
    private String userReplyStatus = "open";

    /**
     * Status to automatically set when staff replies to a ticket (null to disable).
     * Default is awaiting_response
     */
    private String staffReplyStatus = "awaiting_response";

    // ==================== Reminder Configuration ====================

    /**
     * Whether ticket reminders are enabled
     */
    private boolean remindersEnabled = false;

    /**
     * List of reminder thresholds with escalating intervals.
     * Initializes with two defaults - 12h and 24h reminders
     */
    private List<TicketReminderThreshold> reminderThresholds = new ArrayList<>(List.of(
        new TicketReminderThreshold(12, "This ticket needs attention.", true),
        new TicketReminderThreshold(24, "Urgent: This ticket has been waiting for 24 hours!", true)
    ));

    /**
     * How often to check for tickets needing reminders (in minutes)
     */
    private int reminderCheckIntervalMinutes = 30;

    // ==================== Template Configuration ====================

    /**
     * Whether to use modal-based ticket creation instead of DM flow
     */
    private boolean useModalFlow = true;

    /**
     * Templates for modal-based ticket creation per category
     */
    private List<TicketTemplate> templates = new ArrayList<>();

    // ==================== General Configuration ====================

    /**
     * Maximum number of open tickets a user can have at once.
     * By default, users can have 3 maximum open tickets.
     */
    private int maxOpenTicketsPerUser = 3;

    /**
     * Time in seconds between role pings for the same ticket
     */
    private int timeBetweenPings = 60;

    /**
     * Whether to store transcripts in the database
     */
    private boolean storeTranscripts = true;

    /**
     * Whether to upload a transcript file when a ticket is closed
     */
    private boolean uploadTranscriptOnClose = true;

    /**
     * Whether to automatically close stale tickets
     */
    private boolean autoCloseEnabled = false;

    /**
     * Number of days a ticket must be in AWAITING_RESPONSE before auto-closing
     */
    private int autoCloseDays = 7;

    /**
     * Status ID that triggers auto-close countdown (defaults to awaiting_response)
     */
    private String autoCloseStatusId = "awaiting_response";

    /**
     * Message sent when a ticket is auto-closed
     */
    private String autoCloseMessage = "This ticket has been automatically closed due to inactivity.";

    // ==================== Helper Methods ====================

    /**
     * Get a status configuration by its ID
     *
     * @param statusId the status ID to look up
     *
     * @return the status config if found
     */
    public Optional<TicketStatusConfig> getStatusById(String statusId) {
        if (statusId == null) {
            return Optional.empty();
        }
        return statuses.stream()
            .filter(s -> s.getId().equalsIgnoreCase(statusId))
            .findFirst();
    }

    /**
     * Get the default open status configuration
     *
     * @return the default open status, or the first status if none marked as default
     */
    public TicketStatusConfig getDefaultOpenStatus() {
        return statuses.stream()
            .filter(TicketStatusConfig::isDefaultOpen)
            .findFirst()
            .orElse(statuses.isEmpty() ? null : statuses.getFirst());
    }

    /**
     * Get all closed state statuses
     *
     * @return list of statuses that represent closed tickets
     */
    public List<TicketStatusConfig> getClosedStatuses() {
        return statuses.stream()
            .filter(TicketStatusConfig::isClosedState)
            .toList();
    }

    /**
     * Check if a status ID represents a closed state
     *
     * @param statusId the status ID to check
     *
     * @return true if the status is a closed state
     */
    public boolean isClosedStatus(String statusId) {
        return getStatusById(statusId)
            .map(TicketStatusConfig::isClosedState)
            .orElse(false);
    }

    /**
     * Get the display name for a status ID
     *
     * @param statusId the status ID
     *
     * @return the display name, or the ID if not found
     */
    public String getStatusDisplayName(String statusId) {
        return getStatusById(statusId)
            .map(TicketStatusConfig::getDisplayName)
            .orElse(statusId);
    }

    /**
     * Get a category by its ID
     *
     * @param categoryId the category ID to look up
     *
     * @return the category if found
     */
    public Optional<TicketCategory> getCategoryById(String categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return categories.stream()
            .filter(c -> c.getId().equalsIgnoreCase(categoryId))
            .findFirst();
    }

    /**
     * Get a template for a specific category
     *
     * @param categoryId the category ID
     *
     * @return the template if found
     */
    public Optional<TicketTemplate> getTemplateForCategory(String categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return templates.stream()
            .filter(t -> t.getCategoryId().equalsIgnoreCase(categoryId))
            .findFirst();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class TicketCategory {
        private String id;
        private String displayName;
        private String description;
    }
}