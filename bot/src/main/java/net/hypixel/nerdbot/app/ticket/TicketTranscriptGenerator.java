package net.hypixel.nerdbot.app.ticket;

import lombok.experimental.UtilityClass;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketFieldValue;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility responsible for rendering ticket metadata and messages
 * into a simple text transcript that can be logged or exported.
 */
@UtilityClass
public class TicketTranscriptGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Produce a human-readable transcript for the provided ticket, including
     * metadata and every stored message.
     *
     * @param ticket ticket to render
     * @param config ticket configuration used to resolve names
     *
     * @return formatted transcript string
     */
    public static String generate(Ticket ticket, TicketConfig config) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("=".repeat(60)).append("\n");
        sb.append("TICKET TRANSCRIPT\n");
        sb.append("=".repeat(60)).append("\n\n");

        // Data
        sb.append("Ticket ID: ").append(ticket.getFormattedTicketId()).append("\n");
        sb.append("Owner ID: ").append(ticket.getOwnerId()).append("\n");
        sb.append("Category: ").append(getCategoryDisplayName(ticket.getTicketCategoryId(), config)).append("\n");
        sb.append("Status: ").append(config.getStatusDisplayName(ticket.getStatus())).append("\n");
        sb.append("Created: ").append(formatTimestamp(ticket.getCreatedAt())).append("\n");

        if (ticket.getClosedAt() > 0) {
            sb.append("Closed: ").append(formatTimestamp(ticket.getClosedAt())).append("\n");
            sb.append("Closed By: ").append(ticket.getClosedById()).append("\n");
            if (ticket.getCloseReason() != null) {
                sb.append("Close Reason: ").append(ticket.getCloseReason()).append("\n");
            }
        }

        if (ticket.isClaimed()) {
            sb.append("Claimed By: ").append(ticket.getClaimedById()).append("\n");
        }

        // Custom Fields
        if (ticket.hasCustomFields()) {
            sb.append("\n").append("-".repeat(60)).append("\n");
            sb.append("SUBMITTED FIELDS\n");
            sb.append("-".repeat(60)).append("\n\n");

            for (TicketFieldValue field : ticket.getCustomFieldValues()) {
                sb.append(field.getLabel()).append(":\n");
                sb.append(field.getValue()).append("\n\n");
            }
        }

        sb.append("\n").append("-".repeat(60)).append("\n");
        sb.append("MESSAGES\n");
        sb.append("-".repeat(60)).append("\n\n");

        // Messages
        if (ticket.getMessages() != null) {
            for (TicketMessage msg : ticket.getMessages()) {
                sb.append("[").append(formatTimestamp(msg.getTimestamp())).append("] ");
                sb.append(msg.isStaff() ? "[STAFF] " : "[USER] ");
                sb.append(msg.getAuthorName()).append(" (").append(msg.getAuthorId()).append("):\n");
                sb.append(msg.getContent()).append("\n");

                if (msg.getAttachmentUrls() != null && !msg.getAttachmentUrls().isEmpty()) {
                    sb.append("Attachments:\n");
                    for (String url : msg.getAttachmentUrls()) {
                        sb.append("  - ").append(url).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // Footer
        sb.append("=".repeat(60)).append("\n");
        sb.append("END OF TRANSCRIPT\n");
        sb.append("=".repeat(60)).append("\n");

        return sb.toString();
    }

    /**
     * Resolve a category display name from the configuration.
     *
     * @param categoryId configured category ID
     * @param config     ticket configuration
     *
     * @return display name if available, otherwise the raw ID
     */
    private static String getCategoryDisplayName(String categoryId, TicketConfig config) {
        return config.getCategoryById(categoryId)
            .map(TicketConfig.TicketCategory::getDisplayName)
            .orElse(categoryId);
    }

    /**
     * Format an epoch timestamp using the transcript formatter.
     *
     * @param epochMillis epoch milliseconds
     *
     * @return formatted datetime string
     */
    private static String formatTimestamp(long epochMillis) {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            ZoneId.systemDefault()
        ).format(FORMATTER);
    }
}