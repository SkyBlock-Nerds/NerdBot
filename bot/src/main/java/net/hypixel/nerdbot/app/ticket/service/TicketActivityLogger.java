package net.hypixel.nerdbot.app.ticket.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

/**
 * Logs ticket activity events to a configured Discord channel.
 * This provides staff with a centralized audit log of ticket operations.
 */
@Slf4j
public class TicketActivityLogger {

    private final TicketConfig config;

    public TicketActivityLogger(TicketConfig config) {
        this.config = config;
    }

    /**
     * Log that a ticket was created.
     *
     * @param ticket  the created ticket
     * @param creator the user who created the ticket
     */
    public void logCreated(Ticket ticket, User creator) {
        String categoryName = config.getCategoryDisplayName(ticket.getTicketCategoryId());
        sendLog("[%s] Created by %s in **%s**",
            ticket.getFormattedTicketId(), creator.getAsMention(), categoryName);
    }

    /**
     * Log that a ticket was claimed.
     *
     * @param ticket the claimed ticket
     * @param staff  the staff member who claimed it
     */
    public void logClaimed(Ticket ticket, User staff) {
        sendLog("[%s] Claimed by %s",
            ticket.getFormattedTicketId(), staff.getAsMention());
    }

    /**
     * Log a status change.
     *
     * @param ticket    the ticket
     * @param oldStatus the previous status
     * @param newStatus the new status
     * @param actor     the user who made the change
     */
    public void logStatusChange(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, User actor) {
        String oldName = config.getStatusDisplayName(oldStatus);
        String newName = config.getStatusDisplayName(newStatus);
        sendLog("[%s] Status: **%s** -> **%s** by %s",
            ticket.getFormattedTicketId(), oldName, newName, actor.getAsMention());
    }

    /**
     * Log that a ticket was closed.
     *
     * @param ticket the closed ticket
     * @param staff  the staff member who closed it (may be null for auto-close)
     * @param reason the close reason
     */
    public void logClosed(Ticket ticket, User staff, String reason) {
        String closedBy = staff != null ? staff.getAsMention() : "Auto-close";
        String reasonText = reason != null && !reason.isBlank() ? reason : "No reason provided";
        sendLog("[%s] Closed by %s: \"%s\"",
            ticket.getFormattedTicketId(), closedBy, truncate(reasonText, 100));
    }

    /**
     * Log that a ticket was reopened.
     *
     * @param ticket the reopened ticket
     * @param staff  the staff member who reopened it
     */
    public void logReopened(Ticket ticket, User staff) {
        sendLog("[%s] Reopened by %s",
            ticket.getFormattedTicketId(), staff.getAsMention());
    }

    /**
     * Log that a ticket was transferred.
     *
     * @param ticket the transferred ticket
     * @param from   the previous assignee
     * @param to     the new assignee
     * @param actor  the user who performed the transfer
     */
    public void logTransferred(Ticket ticket, User from, User to, User actor) {
        String fromName = from != null ? from.getAsMention() : "Unclaimed";
        sendLog("[%s] Transferred from %s to %s by %s",
            ticket.getFormattedTicketId(), fromName, to.getAsMention(), actor.getAsMention());
    }

    /**
     * Log that a ticket was auto-deleted.
     *
     * @param ticket the deleted ticket
     */
    public void logAutoDeleted(Ticket ticket) {
        sendLog("[%s] Auto-deleted (retention period expired)",
            ticket.getFormattedTicketId());
    }

    /**
     * Send a log message to the activity log channel.
     *
     * @param format the message format string
     * @param args   format arguments
     */
    private void sendLog(String format, Object... args) {
        if (!config.isActivityLogEnabled()) {
            return;
        }

        TextChannel channel = getLogChannel();
        if (channel == null) {
            return;
        }

        String message = String.format(format, args);
        channel.sendMessage(message).queue(
            success -> {
            },
            error -> log.warn("Failed to send activity log message: {}", error.getMessage())
        );
    }

    /**
     * Get the configured activity log channel.
     *
     * @return the text channel, or null if not configured or not found
     */
    private TextChannel getLogChannel() {
        String channelId = config.getActivityLogChannelId();
        if (channelId == null || channelId.isEmpty()) {
            return null;
        }

        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(channelId);
        if (channel == null) {
            log.debug("Activity log channel {} not found", channelId);
        }

        return channel;
    }

    /**
     * Truncate a string to a maximum length, adding ellipsis if truncated.
     *
     * @param text      the text to truncate
     * @param maxLength maximum length
     *
     * @return truncated string
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength - 3) + "...";
    }
}