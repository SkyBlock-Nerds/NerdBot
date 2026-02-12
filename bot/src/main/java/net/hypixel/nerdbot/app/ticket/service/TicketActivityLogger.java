package net.hypixel.nerdbot.app.ticket.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.StringUtils;

import java.time.Instant;

/**
 * Logs ticket activity events to a configured Discord channel using embeds.
 */
@Slf4j
public class TicketActivityLogger {

    private static final int COLOR_CREATED = 0x57F287;    // Green
    private static final int COLOR_CLAIMED = 0x5865F2;    // Blurple
    private static final int COLOR_STATUS = 0xFEE75C;     // Yellow
    private static final int COLOR_CLOSED = 0xED4245;     // Red
    private static final int COLOR_REOPENED = 0x57F287;   // Green
    private static final int COLOR_TRANSFERRED = 0x5865F2; // Blurple
    private static final int COLOR_DELETED = 0x99AAB5;    // Gray

    private final TicketConfig config;

    public TicketActivityLogger(TicketConfig config) {
        this.config = config;
    }

    public void logCreated(Ticket ticket, User creator) {
        String categoryName = config.getCategoryDisplayName(ticket.getTicketCategoryId());
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Created")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Created By", creator.getAsMention(), true)
            .addField("Category", categoryName, true)
            .addField("Channel", "<#" + ticket.getChannelId() + ">", true)
            .setColor(COLOR_CREATED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logClaimed(Ticket ticket, User staff) {
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Claimed")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Claimed By", staff.getAsMention(), true)
            .addField("Channel", "<#" + ticket.getChannelId() + ">", true)
            .setColor(COLOR_CLAIMED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logStatusChange(Ticket ticket, TicketStatus oldStatus, TicketStatus newStatus, User actor) {
        String oldName = config.getStatusDisplayName(oldStatus);
        String newName = config.getStatusDisplayName(newStatus);
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Status Changed")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Status", oldName + " → " + newName, true)
            .addField("Changed By", actor.getAsMention(), true)
            .setColor(COLOR_STATUS)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logClosed(Ticket ticket, User staff, String reason) {
        String closedBy = staff != null ? staff.getAsMention() : "System (Auto-close)";
        String reasonText = reason != null && !reason.isBlank() ? StringUtils.truncate(reason, 200) : "No reason provided";
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Closed")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Closed By", closedBy, true)
            .addField("Reason", reasonText, false)
            .setColor(COLOR_CLOSED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logReopened(Ticket ticket, User staff) {
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Reopened")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Reopened By", staff.getAsMention(), true)
            .addField("Channel", "<#" + ticket.getChannelId() + ">", true)
            .setColor(COLOR_REOPENED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logTransferred(Ticket ticket, User from, User to, User actor) {
        String fromName = from != null ? from.getAsMention() : "Unclaimed";
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Transferred")
            .setDescription(ticket.getFormattedTicketId())
            .addField("From", fromName, true)
            .addField("To", to.getAsMention(), true)
            .addField("By", actor.getAsMention(), true)
            .setColor(COLOR_TRANSFERRED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    public void logAutoDeleted(Ticket ticket) {
        MessageEmbed embed = new EmbedBuilder()
            .setTitle("Ticket Deleted")
            .setDescription(ticket.getFormattedTicketId())
            .addField("Reason", "Retention period expired", false)
            .setColor(COLOR_DELETED)
            .setTimestamp(Instant.now())
            .build();
        sendEmbed(embed);
    }

    private void sendEmbed(MessageEmbed embed) {
        if (!config.isActivityLogEnabled()) {
            return;
        }

        TextChannel channel = getLogChannel();
        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(embed).queue(
            success -> {},
            error -> log.warn("Failed to send activity log: {}", error.getMessage())
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
}