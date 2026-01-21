package net.hypixel.nerdbot.app.ticket.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketReminderThreshold;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

/**
 * Sends ticket notifications via DM (closure/reopen) and reminder messages to channels.
 */
@Slf4j
public class TicketNotificationService {

    private final TicketConfig config;

    public TicketNotificationService(TicketConfig config) {
        this.config = config;
    }

    /**
     * Notify the ticket owner via DM that their ticket was closed.
     *
     * @param ticket   ticket that was closed
     * @param closedBy staff user who closed it (null for auto-close)
     * @param reason   optional closure reason
     */
    public void notifyTicketClosed(Ticket ticket, User closedBy, String reason) {
        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        if (owner == null) {
            log.debug("Cannot notify closure - ticket owner {} not found", ticket.getOwnerId());
            return;
        }

        String closedByText = closedBy != null ? closedBy.getEffectiveName() : "System (Auto-close)";

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Ticket Closed")
            .setDescription("Your ticket **" + ticket.getFormattedTicketId() + "** has been closed.")
            .setColor(0xED4245) // Red
            .addField("Closed By", closedByText, true)
            .addField("Reason", reason != null ? reason : "No reason provided", true)
            .setFooter("If you need further assistance, feel free to open a new ticket.")
            .setTimestamp(java.time.Instant.now());

        owner.openPrivateChannel()
            .flatMap(ch -> ch.sendMessageEmbeds(embed.build()))
            .queue(
                success -> log.debug("Notified user {} of ticket close", owner.getId()),
                error -> log.error("Failed to notify user {} of ticket close", owner.getId(), error)
            );
    }

    /**
     * Notify the ticket owner via DM that their ticket was reopened.
     *
     * @param ticket     ticket that was reopened
     * @param reopenedBy staff user who reopened it
     */
    public void notifyTicketReopened(Ticket ticket, User reopenedBy) {
        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        if (owner == null) {
            log.debug("Cannot notify reopen - ticket owner {} not found", ticket.getOwnerId());
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Ticket Reopened")
            .setDescription("Your ticket **" + ticket.getFormattedTicketId() + "** has been reopened.")
            .setColor(0x57F287) // Green
            .addField("Reopened By", reopenedBy.getEffectiveName(), true)
            .addField("Channel", "<#" + ticket.getChannelId() + ">", true)
            .setFooter("You can continue the conversation in the ticket channel.")
            .setTimestamp(java.time.Instant.now());

        owner.openPrivateChannel()
            .flatMap(ch -> ch.sendMessageEmbeds(embed.build()))
            .queue(
                success -> log.debug("Notified user {} of ticket reopen", owner.getId()),
                error -> log.error("Failed to notify user {} of ticket reopen", owner.getId(), error)
            );
    }

    /**
     * Post a reminder message into the ticket channel.
     *
     * @param ticket             ticket being reminded
     * @param threshold          reminder configuration triggering this send
     * @param actualHoursWaiting actual hours the ticket has been waiting
     */
    public void sendReminderToChannel(Ticket ticket, TicketReminderThreshold threshold, long actualHoursWaiting) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            log.debug("Cannot send reminder - channel {} not found", ticket.getChannelId());
            return;
        }

        String reminderText = threshold.getMessage().replace("{hours}", String.valueOf(actualHoursWaiting));
        StringBuilder message = new StringBuilder();
        message.append(":bell: **Reminder:** ").append(reminderText);

        if (threshold.isPingStaff() && config.getTicketRoleId() != null && !config.getTicketRoleId().isEmpty()) {
            message.append("\n<@&").append(config.getTicketRoleId()).append(">");
        }

        channel.sendMessage(message.toString()).queue(
            null,
            error -> log.error("Failed to send reminder to channel {}", ticket.getChannelId(), error)
        );
    }

    /**
     * Send an embed to the ticket channel notifying that the ticket was claimed.
     *
     * @param ticket    ticket that was claimed
     * @param claimedBy staff user who claimed it
     */
    public void notifyTicketClaimedInChannel(Ticket ticket, User claimedBy) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            log.debug("Cannot notify claim - channel {} not found", ticket.getChannelId());
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Ticket Claimed")
            .setDescription(claimedBy.getAsMention() + " is now handling this ticket.")
            .setColor(0x5865F2)
            .setTimestamp(java.time.Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
            null,
            error -> log.error("Failed to send claim notification to channel {}", ticket.getChannelId(), error)
        );
    }

    /**
     * Send a message to a ticket channel with proper error handling.
     *
     * @param ticket  the ticket
     * @param message the message to send
     */
    public void sendToChannel(Ticket ticket, String message) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            log.debug("Cannot send message - channel {} not found", ticket.getChannelId());
            return;
        }

        channel.sendMessage(message).queue(
            null,
            error -> log.error("Failed to send message to channel {}", ticket.getChannelId(), error)
        );
    }
}