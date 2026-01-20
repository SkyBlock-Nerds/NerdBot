package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Getter
@Setter
@ToString
public class TicketConfig {

    /**
     * The ID of the category where ticket channels will be created
     */
    private String ticketCategoryId = "";

    /**
     * The ID of the category where closed ticket channels will be moved (optional).
     * If empty, closed ticket channels will be deleted instead of moved.
     */
    private String closedTicketCategoryId = "";

    /**
     * Prefix for ticket channel names (e.g., "ticket-" results in "ticket-0001-username")
     */
    private String ticketChannelPrefix = "ticket-";

    /**
     * The ID of the role to ping for new tickets
     */
    private String ticketRoleId = "";

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
     * Display settings for ticket statuses (display name, emoji, and allowed transitions).
     * Status types are fixed by {@link TicketStatus} enum.
     * Default transitions:
     * - OPEN -> IN_PROGRESS, AWAITING_RESPONSE, CLOSED
     * - IN_PROGRESS -> AWAITING_RESPONSE, CLOSED
     * - AWAITING_RESPONSE -> OPEN, IN_PROGRESS, CLOSED
     * - CLOSED -> OPEN (reopen)
     */
    private List<TicketStatusConfig> statuses = new ArrayList<>(List.of(
        new TicketStatusConfig("open", "Open", "\uD83D\uDFE2", List.of(
            new TicketStatusTransition("in_progress", "Start Working", false, true),
            new TicketStatusTransition("awaiting_response", "Awaiting Response"),
            new TicketStatusTransition("closed", "Close")
        )),
        new TicketStatusConfig("in_progress", "In Progress", "\uD83D\uDFE1", List.of(
            new TicketStatusTransition("awaiting_response", "Awaiting Response"),
            new TicketStatusTransition("closed", "Close")
        )),
        new TicketStatusConfig("awaiting_response", "Awaiting Response", "\uD83D\uDFE0", List.of(
            new TicketStatusTransition("open", "Reopen"),
            new TicketStatusTransition("in_progress", "Resume Working"),
            new TicketStatusTransition("closed", "Close")
        )),
        new TicketStatusConfig("closed", "Closed", "\uD83D\uDD34", List.of(
            new TicketStatusTransition("open", "Reopen")
        ))
    ));

    // ==================== Auto-Status Configuration ====================

    /**
     * Status to automatically set when a user replies to a ticket (null to disable).
     */
    private TicketStatus userReplyStatus = TicketStatus.OPEN;

    /**
     * Status to automatically set when staff replies to a ticket (null to disable).
     */
    private TicketStatus staffReplyStatus = TicketStatus.AWAITING_RESPONSE;

    // ==================== Reminder Configuration ====================

    /**
     * Whether ticket reminders are enabled
     */
    private boolean remindersEnabled = false;

    /**
     * List of reminder thresholds with escalating intervals.
     * Initializes with two defaults - 12h and 24h reminders.
     * Use {hours} placeholder to show actual elapsed hours.
     */
    private List<TicketReminderThreshold> reminderThresholds = new ArrayList<>(List.of(
        new TicketReminderThreshold(12, "This ticket needs attention - it's been waiting for {hours} hours.", true),
        new TicketReminderThreshold(24, "Urgent: This ticket has been waiting for {hours} hours!", true)
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
     * Status that triggers auto-close countdown.
     */
    private TicketStatus autoCloseStatus = TicketStatus.AWAITING_RESPONSE;

    /**
     * Message sent when a ticket is auto-closed
     */
    private String autoCloseMessage = "This ticket has been automatically closed due to inactivity.";

    /**
     * Whether to automatically delete closed tickets after a retention period
     */
    private boolean autoDeleteClosedTickets = false;

    /**
     * Number of days to retain closed tickets before deletion.
     * After this period, both the MongoDB record and Discord thread will be deleted.
     */
    private int closedTicketRetentionDays = 30;

    // ==================== Blacklist Configuration ====================

    /**
     * Set of user IDs that are blocked from creating tickets.
     */
    private Set<String> blacklistedUserIds = new HashSet<>();

    /**
     * Message shown to blacklisted users when they try to create a ticket.
     */
    private String blacklistMessage = "You are not permitted to create tickets.";

    // ==================== Activity Log Configuration ====================

    /**
     * Channel ID for logging ticket activity. If empty or null, activity logging is disabled.
     */
    private String activityLogChannelId = "";

    // ==================== Helper Methods ====================

    /**
     * Get display configuration for a status.
     *
     * @param status the ticket status
     *
     * @return the status config if found
     */
    public Optional<TicketStatusConfig> getStatusConfig(TicketStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return statuses.stream()
            .filter(s -> s.getId().equalsIgnoreCase(status.getId()))
            .findFirst();
    }

    /**
     * Get the display name for a status.
     *
     * @param status the ticket status
     *
     * @return the display name, or the enum name if not configured
     */
    public String getStatusDisplayName(TicketStatus status) {
        return getStatusConfig(status)
            .map(TicketStatusConfig::getDisplayName)
            .orElse(status.name());
    }

    /**
     * Get the emoji for a status.
     *
     * @param status the ticket status
     *
     * @return the emoji if configured, or empty
     */
    public Optional<String> getStatusEmoji(TicketStatus status) {
        return getStatusConfig(status)
            .map(TicketStatusConfig::getEmoji)
            .filter(e -> !e.isEmpty());
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
     * Get the display name for a category, falling back to the ID if not found.
     *
     * @param categoryId the category ID
     *
     * @return the display name or the ID if not found
     */
    public String getCategoryDisplayName(String categoryId) {
        return getCategoryById(categoryId)
            .map(TicketCategory::getDisplayName)
            .orElse(categoryId);
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

    // ==================== Transition Helper Methods ====================

    /**
     * Check if a status transition is allowed.
     *
     * @param fromStatus the current status
     * @param toStatus   the target status
     *
     * @return true if the transition is allowed
     */
    public boolean isTransitionAllowed(TicketStatus fromStatus, TicketStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }

        return getStatusConfig(fromStatus)
            .map(config -> config.canTransitionTo(toStatus.getId()))
            .orElse(false);
    }

    /**
     * Get the allowed transitions from a status.
     *
     * @param fromStatus the current status
     *
     * @return list of allowed transitions (empty if none configured)
     */
    public List<TicketStatusTransition> getAllowedTransitions(TicketStatus fromStatus) {
        if (fromStatus == null) {
            return List.of();
        }

        return getStatusConfig(fromStatus)
            .map(TicketStatusConfig::getAllowedTransitions)
            .orElse(List.of());
    }

    /**
     * Get the transition config for a specific status change.
     *
     * @param fromStatus the current status
     * @param toStatus   the target status
     *
     * @return the transition config, or null if not allowed
     */
    public TicketStatusTransition getTransition(TicketStatus fromStatus, TicketStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return null;
        }

        return getStatusConfig(fromStatus)
            .map(config -> config.getTransition(toStatus.getId()))
            .orElse(null);
    }

    // ==================== Blacklist Helper Methods ====================

    /**
     * Check if a user is blacklisted from creating tickets.
     *
     * @param userId the user ID to check
     *
     * @return true if the user is blacklisted
     */
    public boolean isUserBlacklisted(String userId) {
        return blacklistedUserIds != null && blacklistedUserIds.contains(userId);
    }

    /**
     * Add a user to the blacklist.
     *
     * @param userId the user ID to blacklist
     *
     * @return true if the user was added, false if already blacklisted
     */
    public boolean addToBlacklist(String userId) {
        if (blacklistedUserIds == null) {
            blacklistedUserIds = new HashSet<>();
        }

        return blacklistedUserIds.add(userId);
    }

    /**
     * Remove a user from the blacklist.
     *
     * @param userId the user ID to remove
     *
     * @return true if the user was removed, false if not in blacklist
     */
    public boolean removeFromBlacklist(String userId) {
        if (blacklistedUserIds == null) {
            return false;
        }

        return blacklistedUserIds.remove(userId);
    }

    // ==================== Activity Log Helper Methods ====================

    /**
     * Check if activity logging is enabled.
     *
     * @return true if activity log channel is configured
     */
    public boolean isActivityLogEnabled() {
        return activityLogChannelId != null && !activityLogChannelId.isEmpty();
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