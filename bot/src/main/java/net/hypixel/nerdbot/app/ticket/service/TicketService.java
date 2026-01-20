package net.hypixel.nerdbot.app.ticket.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.ticket.TicketValidation;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketReminderThreshold;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.ChannelIndexStats;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketMessage;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketReservationResult;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.storage.database.repository.TicketMessageArchiveRepository;
import net.hypixel.nerdbot.discord.storage.database.repository.TicketRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Central service that handles ticket creation, storage, reminders, and status management.
 * Delegates Discord-specific operations to {@link TicketDiscordService} and notification
 * operations to {@link TicketNotificationService}.
 */
@Slf4j
public class TicketService {

    private static volatile TicketService instance;

    @Getter
    private final TicketConfig config;
    @Getter
    private final TicketRepository ticketRepository;
    private final TicketMessageArchiveRepository messageArchiveRepository;
    @Getter
    private final TicketDiscordService discordService;
    @Getter
    private final TicketNotificationService notificationService;
    @Getter
    private final TicketActivityLogger activityLogger;

    /**
     * Construct a new ticket service instance.
     *
     * @param config                   ticket configuration backing this service
     * @param ticketRepository         repository used for ticket persistence
     * @param messageArchiveRepository repository used for message overflow archiving
     */
    private TicketService(TicketConfig config, TicketRepository ticketRepository,
                          TicketMessageArchiveRepository messageArchiveRepository) {
        this.config = config;
        this.ticketRepository = ticketRepository;
        this.messageArchiveRepository = messageArchiveRepository;
        this.discordService = new TicketDiscordService(config, ticketRepository);
        this.notificationService = new TicketNotificationService(config);
        this.activityLogger = new TicketActivityLogger(config);

        this.ticketRepository.initializeCounterFromExistingTickets();
        this.ticketRepository.loadChannelIndex();
        this.ticketRepository.cacheOpenTickets();
        pruneTicketsWithMissingChannels();

        // Initialize open tickets gauge on startup
        initializeTicketGauges();
    }

    /**
     * Get the singleton instance for ticket operations.
     *
     * @return {@link TicketService} instance
     */
    public static TicketService getInstance() {
        if (instance == null) {
            synchronized (TicketService.class) {
                if (instance == null) {
                    TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

                    TicketRepository repository = BotEnvironment.getBot().getDatabase()
                        .getRepositoryManager().getRepository(TicketRepository.class);
                    TicketMessageArchiveRepository archiveRepository = BotEnvironment.getBot().getDatabase()
                        .getRepositoryManager().getRepository(TicketMessageArchiveRepository.class);
                    instance = new TicketService(config, repository, archiveRepository);
                }
            }
        }
        return instance;
    }

    /**
     * Initialize Prometheus gauges with current ticket counts.
     * Called once on startup to set initial values.
     */
    private void initializeTicketGauges() {
        try {
            List<Ticket> openTickets = ticketRepository.findOpenTickets();

            // Reset gauges first
            PrometheusMetrics.TICKETS_OPEN.clear();
            PrometheusMetrics.TICKETS_CLAIMED.clear();

            // Count by status
            for (TicketStatus status : TicketStatus.values()) {
                if (!status.isClosedState()) {
                    long count = openTickets.stream()
                        .filter(t -> t.getStatus() == status)
                        .count();
                    PrometheusMetrics.TICKETS_OPEN.labels(status.getId()).set(count);
                }
            }

            // Count by claimed staff
            openTickets.stream()
                .filter(Ticket::isClaimed)
                .collect(java.util.stream.Collectors.groupingBy(Ticket::getClaimedById, java.util.stream.Collectors.counting()))
                .forEach((staffId, count) -> PrometheusMetrics.TICKETS_CLAIMED.labels(staffId).set(count));

            log.info("Initialized ticket gauges: {} open tickets", openTickets.size());
        } catch (Exception e) {
            log.warn("Failed to initialize ticket gauges: {}", e.getMessage());
        }
    }

    /**
     * Get the configured ticket category for creating new tickets.
     *
     * @return optional category reference
     */
    public Optional<Category> getTicketCategory() {
        return discordService.getTicketCategory();
    }

    /**
     * Get the display name for a status.
     *
     * @param status the ticket status
     *
     * @return the display name
     */
    public String getStatusDisplayName(TicketStatus status) {
        return config.getStatusDisplayName(status);
    }

    /**
     * Create a ticket channel on behalf of the given user.
     *
     * @param user        ticket owner
     * @param categoryId  configured category identifier
     * @param description problem description supplied by the user
     *
     * @return persisted ticket instance
     *
     * @throws IllegalArgumentException if input validation fails
     * @throws IllegalStateException    if max tickets reached or channel not found
     */
    public Ticket createTicket(User user, String categoryId, String description) {
        // Validate inputs
        TicketValidation.validateUserNotBlacklisted(user.getId(), config);
        TicketValidation.validateCategoryId(categoryId, config);
        TicketValidation.validateDescription(description);

        if (getTicketCategory().isEmpty()) {
            throw new IllegalStateException("Ticket category not found");
        }

        // Atomically check max tickets and reserve a ticket number
        // This prevents race conditions where concurrent requests could both pass the limit check
        TicketReservationResult reservation = ticketRepository.reserveTicketSlot(
            user.getId(),
            config.getMaxOpenTicketsPerUser()
        );

        if (!reservation.success()) {
            throw new IllegalStateException(reservation.errorMessage());
        }

        // Create the ticket entity with the reserved number
        int ticketNumber = reservation.ticketNumber();
        Ticket ticket = new Ticket(ticketNumber, user.getId());
        ticket.setTicketCategoryId(categoryId);

        String categoryName = config.getCategories().stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .map(TicketConfig.TicketCategory::getDisplayName)
            .orElse(categoryId);

        // Delegate channel creation to Discord service
        Optional<TextChannel> channelOpt = discordService.createTicketChannel(ticket, user, categoryName, description);
        if (channelOpt.isEmpty()) {
            throw new IllegalStateException("Failed to create ticket channel");
        }

        TextChannel channel = channelOpt.get();
        ticket.setChannelId(channel.getId());

        // Record the initial message
        TicketMessage initMessage = new TicketMessage(user.getId(), user.getEffectiveName(), description, false);
        ticket.addMessage(initMessage);

        // Persist
        ticketRepository.cacheObject(ticket);
        ticketRepository.saveToDatabase(ticket);

        // Metrics
        PrometheusMetrics.TICKETS_CREATED.labels(categoryId).inc();
        PrometheusMetrics.TICKETS_OPEN.labels(TicketStatus.OPEN.getId()).inc();

        // Activity log
        activityLogger.logCreated(ticket, user);

        log.info("Created ticket {} for user {} (Channel: {})", ticket.getFormattedTicketId(), user.getId(), channel.getId());

        return ticket;
    }

    /**
     * Handle a message posted directly into the ticket channel.
     *
     * @param author      message author
     * @param channel     target ticket channel
     * @param content     textual content from the message
     * @param attachments uploaded attachments on the message
     * @param isStaff     whether the author is staff (affects auto-status rules)
     */
    public void handleTicketMessage(User author, TextChannel channel, String content, List<Message.Attachment> attachments, boolean isStaff) {
        Optional<Ticket> ticketOpt = ticketRepository.findByChannelId(channel.getId());
        if (ticketOpt.isEmpty()) {
            return;
        }

        Ticket ticket = ticketOpt.get();

        TicketMessage msg = new TicketMessage();
        msg.setMessageId(channel.getLatestMessageId());
        msg.setAuthorId(author.getId());
        msg.setAuthorName(author.getEffectiveName());
        msg.setContent(content);
        msg.setAttachmentUrls(attachments.stream().map(Message.Attachment::getUrl).toList());
        msg.setTimestamp(System.currentTimeMillis());
        msg.setStaff(isStaff);

        ticket.addMessage(msg);

        // Metrics
        PrometheusMetrics.TICKET_MESSAGES.labels(isStaff ? "staff" : "user").inc();

        // Track first staff response time
        if (isStaff && ticket.recordFirstStaffResponse()) {
            long responseTimeMs = ticket.getTimeToFirstResponseMs();
            if (responseTimeMs > 0) {
                PrometheusMetrics.TICKET_FIRST_RESPONSE_TIME.observe(responseTimeMs / 1000.0);
            }
        }

        // Archive overflow messages if needed
        archiveOverflowMessages(ticket);

        // Auto-status on staff reply
        if (isStaff && config.getStaffReplyStatus() != null) {
            TicketStatus targetStatus = config.getStaffReplyStatus();
            if (ticket.getStatus() != targetStatus && !ticket.getStatus().isClosedState()) {
                TicketStatus oldStatus = ticket.getStatus();
                ticket.setStatus(targetStatus);
                ticket.resetReminderTracking();
                discordService.updateChannelName(ticket);
                log.info("Auto-updated ticket {} status from {} to {} after staff reply",
                    ticket.getFormattedTicketId(), oldStatus, targetStatus);
            }
        }

        ticketRepository.saveToDatabase(ticket);
        // Note: No DM forwarding needed - users communicate directly in the private channel
    }

    /**
     * Handle a DM reply from the ticket owner which should be mirrored to the channel.
     *
     * @param user        ticket owner replying over DM
     * @param channelId   channel to mirror the reply into
     * @param content     message content
     * @param attachments uploaded attachments accompanying the DM
     *
     * @return result indicating success or failure with reason
     */
    public UserReplyResult handleUserReply(User user, String channelId, String content, List<Message.Attachment> attachments) {
        Optional<Ticket> ticketOpt = ticketRepository.findByChannelId(channelId);
        if (ticketOpt.isEmpty()) {
            log.debug("Ticket not found for channel {} when user {} tried to reply", channelId, user.getId());
            return UserReplyResult.fail("This ticket could not be found. It may have been deleted.");
        }

        Ticket ticket = ticketOpt.get();

        if (!ticket.getOwnerId().equals(user.getId())) {
            log.debug("User {} tried to reply to ticket {} but doesn't own it (owner: {})",
                user.getId(), ticket.getFormattedTicketId(), ticket.getOwnerId());
            return UserReplyResult.fail("You don't have permission to reply to this ticket.");
        }

        if (ticket.isClosed()) {
            log.debug("User {} tried to reply to closed ticket {}", user.getId(), ticket.getFormattedTicketId());
            return UserReplyResult.fail("This ticket has been closed. Please open a new ticket if you need further assistance.");
        }

        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(channelId);
        if (channel == null) {
            log.debug("Channel {} not found in JDA for ticket {}", channelId, ticket.getFormattedTicketId());
            return UserReplyResult.fail("The ticket channel could not be found. It may have been deleted.");
        }

        // Send message to channel with proper error handling
        String prefix = "**" + user.getEffectiveName() + ":** ";
        sendUserReplyToChannel(channel, prefix, content, attachments);

        // Record the message
        TicketMessage ticketMessage = new TicketMessage(user.getId(), user.getEffectiveName(), content, false);
        ticketMessage.setAttachmentUrls(attachments.stream().map(Message.Attachment::getUrl).toList());
        ticket.addMessage(ticketMessage);

        // Archive overflow messages if needed
        archiveOverflowMessages(ticket);

        // Auto-status on user reply
        if (config.getUserReplyStatus() != null) {
            TicketStatus targetStatus = config.getUserReplyStatus();
            if (ticket.getStatus() != targetStatus && !ticket.getStatus().isClosedState()) {
                TicketStatus oldStatus = ticket.getStatus();
                ticket.setStatus(targetStatus);
                ticket.resetReminderTracking();
                discordService.updateChannelName(ticket);
                log.info("Auto-updated ticket {} status from {} to {} after user reply",
                    ticket.getFormattedTicketId(), oldStatus, targetStatus);
            }
        }

        ticketRepository.saveToDatabase(ticket);

        log.info("User {} replied to ticket {} via DM", user.getId(), ticket.getFormattedTicketId());
        return UserReplyResult.ok();
    }

    private void sendUserReplyToChannel(TextChannel channel, String prefix, String content, List<Message.Attachment> attachments) {
        int maxContentLength = 2000 - prefix.length();

        if (content.length() <= maxContentLength) {
            channel.sendMessage(prefix + content).queue(
                null,
                error -> log.error("Failed to send user reply to channel {}", channel.getId(), error)
            );
        } else {
            channel.sendMessage(prefix + content.substring(0, maxContentLength)).queue(
                null,
                error -> log.error("Failed to send user reply to channel {}", channel.getId(), error)
            );
            String remaining = content.substring(maxContentLength);
            for (int i = 0; i < remaining.length(); i += 2000) {
                String chunk = remaining.substring(i, Math.min(i + 2000, remaining.length()));
                channel.sendMessage(chunk).queue(
                    null,
                    error -> log.error("Failed to send reply chunk to channel {}", channel.getId(), error)
                );
            }
        }

        if (!attachments.isEmpty()) {
            List<FileUpload> uploads = downloadAttachments(attachments);
            if (!uploads.isEmpty()) {
                channel.sendFiles(uploads).queue(
                    null,
                    error -> log.error("Failed to send attachments to channel {}", channel.getId(), error)
                );
            }
        }
    }

    /**
     * Persist a manual status change performed by staff.
     *
     * @param ticket    ticket being updated
     * @param newStatus target status
     * @param changedBy staff member making the change
     */
    public void updateStatus(Ticket ticket, TicketStatus newStatus, User changedBy) {
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        discordService.updateChannelName(ticket);
        ticketRepository.saveToDatabase(ticket);

        // Metrics
        PrometheusMetrics.TICKET_STAFF_ACTIONS.labels("status_change", changedBy.getId()).inc();
        if (!oldStatus.isClosedState()) {
            PrometheusMetrics.TICKETS_OPEN.labels(oldStatus.getId()).dec();
        }
        if (!newStatus.isClosedState()) {
            PrometheusMetrics.TICKETS_OPEN.labels(newStatus.getId()).inc();
        }

        // Activity log
        activityLogger.logStatusChange(ticket, oldStatus, newStatus, changedBy);

        log.info("Ticket {} status changed from {} to {} by {}",
            ticket.getFormattedTicketId(), oldStatus, newStatus, changedBy.getId());
    }

    /**
     * Close a ticket and archive its Discord channel.
     *
     * @param ticket   ticket being closed
     * @param closedBy staff user performing the closure
     * @param reason   optional reason to display to staff/end user
     */
    public void closeTicket(Ticket ticket, User closedBy, String reason) {
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(System.currentTimeMillis());
        ticket.setClosedById(closedBy.getId());
        ticket.setCloseReason(reason);

        // Update Discord channel
        discordService.ensureButtonControllerMessageId(ticket);
        discordService.updateChannelName(ticket);

        if (config.isUploadTranscriptOnClose()) {
            discordService.uploadTranscript(ticket, closedBy, reason);
        }

        discordService.refreshControlPanel(ticket, () -> discordService.archiveChannel(ticket));

        // Persist and update caches
        ticketRepository.saveToDatabase(ticket);
        ticketRepository.onTicketClosed(ticket);

        // Notify user
        notificationService.notifyTicketClosed(ticket, closedBy, reason);

        // Metrics
        PrometheusMetrics.TICKETS_CLOSED.labels("manual").inc();
        if (!oldStatus.isClosedState()) {
            PrometheusMetrics.TICKETS_OPEN.labels(oldStatus.getId()).dec();
        }
        if (ticket.isClaimed()) {
            PrometheusMetrics.TICKETS_CLAIMED.labels(ticket.getClaimedById()).dec();
        }
        // Observe resolution time
        long resolutionTimeMs = ticket.getClosedAt() - ticket.getCreatedAt();
        PrometheusMetrics.TICKET_RESOLUTION_TIME.observe(resolutionTimeMs / 1000.0);
        // Observe message count
        PrometheusMetrics.TICKET_MESSAGE_COUNT.observe(ticket.getTotalMessageCount());

        // Activity log
        activityLogger.logClosed(ticket, closedBy, reason);

        log.info("Ticket {} closed by {}", ticket.getFormattedTicketId(), closedBy.getId());
    }

    /**
     * Reopen a closed ticket
     */
    public void reopenTicket(Ticket ticket, User reopenedBy, String reason) {
        if (!ticket.getStatus().isClosedState()) {
            throw new IllegalStateException("Ticket is not closed");
        }

        ticket.setStatus(TicketStatus.OPEN);
        ticket.setClosedAt(-1);
        ticket.setClosedById(null);
        ticket.setCloseReason(null);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        // Unarchive channel, then update name and send message
        discordService.ensureButtonControllerMessageId(ticket);
        discordService.unarchiveChannel(ticket, () -> {
            discordService.updateChannelName(ticket);
            String reasonText = reason != null ? reason : "No reason provided";
            notificationService.sendToChannel(ticket, "**Ticket Reopened** by <@" + reopenedBy.getId() + ">\n**Reason:** " + reasonText);
            discordService.refreshControlPanel(ticket);
        });

        // Persist and update caches
        ticketRepository.saveToDatabase(ticket);
        ticketRepository.onTicketReopened(ticket);

        // Notify user
        notificationService.notifyTicketReopened(ticket, reopenedBy);

        // Metrics
        PrometheusMetrics.TICKETS_REOPENED.inc();
        PrometheusMetrics.TICKETS_OPEN.labels(TicketStatus.OPEN.getId()).inc();

        // Activity log
        activityLogger.logReopened(ticket, reopenedBy);

        log.info("Ticket {} reopened by {}", ticket.getFormattedTicketId(), reopenedBy.getId());
    }

    /**
     * Claim a ticket for the supplied staff member and update channel metadata.
     *
     * @param ticket ticket being claimed
     * @param staff  staff member taking ownership
     *
     * @throws IllegalStateException if ticket is already claimed by someone else
     */
    public void claimTicket(Ticket ticket, User staff) {
        // Prevent claim theft - only allow claiming if unclaimed or claimed by same user
        if (ticket.isClaimed() && !ticket.getClaimedById().equals(staff.getId())) {
            throw new IllegalStateException("This ticket is already claimed by <@" + ticket.getClaimedById() + ">. Use transfer to reassign.");
        }

        TicketStatus oldStatus = ticket.getStatus();
        ticket.setClaimedById(staff.getId());
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        discordService.updateChannelName(ticket);
        ticketRepository.saveToDatabase(ticket);

        discordService.ensureButtonControllerMessageId(ticket);
        notificationService.sendToChannel(ticket, "**Ticket claimed** by " + staff.getAsMention());
        discordService.refreshControlPanel(ticket);

        // Metrics
        PrometheusMetrics.TICKET_STAFF_ACTIONS.labels("claim", staff.getId()).inc();
        PrometheusMetrics.TICKETS_CLAIMED.labels(staff.getId()).inc();
        if (oldStatus != TicketStatus.IN_PROGRESS && !oldStatus.isClosedState()) {
            PrometheusMetrics.TICKETS_OPEN.labels(oldStatus.getId()).dec();
            PrometheusMetrics.TICKETS_OPEN.labels(TicketStatus.IN_PROGRESS.getId()).inc();
        }

        // Activity log
        activityLogger.logClaimed(ticket, staff);

        log.info("Ticket {} claimed by {}", ticket.getFormattedTicketId(), staff.getId());
    }

    /**
     * Transfer a ticket to another staff member
     */
    public void transferTicket(Ticket ticket, User newStaff, User transferredBy) {
        String previousClaimant = ticket.getClaimedById();
        ticket.setClaimedById(newStaff.getId());
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.saveToDatabase(ticket);

        discordService.ensureButtonControllerMessageId(ticket);
        String message = previousClaimant != null
            ? "**Ticket transferred** from <@" + previousClaimant + "> to " + newStaff.getAsMention() + " by " + transferredBy.getAsMention()
            : "**Ticket assigned** to " + newStaff.getAsMention() + " by " + transferredBy.getAsMention();
        notificationService.sendToChannel(ticket, message);
        discordService.refreshControlPanel(ticket);

        // Metrics
        PrometheusMetrics.TICKET_STAFF_ACTIONS.labels("transfer", transferredBy.getId()).inc();
        if (previousClaimant != null) {
            PrometheusMetrics.TICKETS_CLAIMED.labels(previousClaimant).dec();
        }
        PrometheusMetrics.TICKETS_CLAIMED.labels(newStaff.getId()).inc();

        // Activity log
        User previousUser = previousClaimant != null
            ? DiscordBotEnvironment.getBot().getJDA().getUserById(previousClaimant)
            : null;
        activityLogger.logTransferred(ticket, previousUser, newStaff, transferredBy);

        log.info("Ticket {} transferred from {} to {} by {}",
            ticket.getFormattedTicketId(), previousClaimant, newStaff.getId(), transferredBy.getId());
    }

    /**
     * Auto-close any tickets that have remained in the auto-close status beyond
     * the configured number of days.
     */
    public void closeStaleTickets() {
        if (!config.isAutoCloseEnabled()) {
            return;
        }

        long cutoffTime = System.currentTimeMillis() - (config.getAutoCloseDays() * TimeUnit.DAYS.toMillis(1));
        TicketStatus autoCloseStatus = config.getAutoCloseStatus();

        // Use the optimized repository method that queries MongoDB directly
        List<Ticket> staleTickets = ticketRepository.findStaleTickets(autoCloseStatus, cutoffTime);

        if (staleTickets.isEmpty()) {
            return;
        }

        // Deduplicate by ticket number to avoid processing duplicates
        Map<Integer, Ticket> uniqueTickets = new LinkedHashMap<>();
        for (Ticket ticket : staleTickets) {
            uniqueTickets.putIfAbsent(ticket.getTicketNumber(), ticket);
        }

        log.info("Found {} stale tickets to auto-close", uniqueTickets.size());

        for (Ticket ticket : uniqueTickets.values()) {
            try {
                // Use closeTicketForAutoClose to differentiate from manual close
                closeTicketForAutoClose(ticket, config.getAutoCloseMessage());
                log.info("Auto-closed stale ticket {} (in {} status for {} days)",
                    ticket.getFormattedTicketId(), autoCloseStatus, config.getAutoCloseDays());
            } catch (Exception e) {
                log.error("Failed to auto-close ticket {}", ticket.getFormattedTicketId(), e);
            }
        }
    }

    /**
     * Close a ticket due to auto-close (different metric label than manual close).
     */
    private void closeTicketForAutoClose(Ticket ticket, String reason) {
        TicketStatus oldStatus = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(System.currentTimeMillis());
        ticket.setClosedById(null); // No user for auto-close
        ticket.setCloseReason(reason);

        discordService.ensureButtonControllerMessageId(ticket);
        discordService.updateChannelName(ticket);

        if (config.isUploadTranscriptOnClose()) {
            discordService.uploadTranscript(ticket, null, reason);
        }

        discordService.refreshControlPanel(ticket, () -> discordService.archiveChannel(ticket));

        ticketRepository.saveToDatabase(ticket);
        ticketRepository.onTicketClosed(ticket);

        notificationService.notifyTicketClosed(ticket, null, reason);

        // Metrics - use "auto_close" label
        PrometheusMetrics.TICKETS_CLOSED.labels("auto_close").inc();
        if (!oldStatus.isClosedState()) {
            PrometheusMetrics.TICKETS_OPEN.labels(oldStatus.getId()).dec();
        }
        if (ticket.isClaimed()) {
            PrometheusMetrics.TICKETS_CLAIMED.labels(ticket.getClaimedById()).dec();
        }
        PrometheusMetrics.TICKET_RESOLUTION_TIME.observe((ticket.getClosedAt() - ticket.getCreatedAt()) / 1000.0);
        PrometheusMetrics.TICKET_MESSAGE_COUNT.observe(ticket.getTotalMessageCount());

        // Activity log
        activityLogger.logClosed(ticket, null, reason);
    }

    /**
     * Delete closed tickets that have exceeded the retention period.
     * This removes both the Discord channel and the MongoDB record.
     */
    public void deleteOldClosedTickets() {
        if (!config.isAutoDeleteClosedTickets()) {
            return;
        }

        long retentionCutoff = System.currentTimeMillis() -
            (config.getClosedTicketRetentionDays() * TimeUnit.DAYS.toMillis(1));

        List<Ticket> oldTickets = ticketRepository.findClosedTicketsOlderThan(retentionCutoff);

        if (oldTickets.isEmpty()) {
            return;
        }

        // Deduplicate by ticket number to avoid processing duplicates
        Map<Integer, Ticket> uniqueTickets = new LinkedHashMap<>();
        for (Ticket ticket : oldTickets) {
            uniqueTickets.putIfAbsent(ticket.getTicketNumber(), ticket);
        }

        log.info("Found {} closed tickets older than {} days to delete",
            uniqueTickets.size(), config.getClosedTicketRetentionDays());

        int deletedCount = 0;
        int failedCount = 0;

        for (Ticket ticket : uniqueTickets.values()) {
            try {
                // Delete the Discord channel first
                boolean channelDeleted = discordService.deleteChannel(ticket);

                if (channelDeleted) {
                    // Remove from caches and indexes
                    ticketRepository.removeFromChannelIndex(ticket.getChannelId());

                    // Delete from MongoDB
                    ticketRepository.deleteFromDatabase(String.valueOf(ticket.getTicketNumber()));

                    // Metrics
                    PrometheusMetrics.TICKETS_CLOSED.labels("auto_delete").inc();

                    // Activity log
                    activityLogger.logAutoDeleted(ticket);

                    log.info("Deleted old closed ticket {} (closed {} days ago)",
                        ticket.getFormattedTicketId(),
                        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - ticket.getClosedAt()));
                    deletedCount++;
                } else {
                    log.warn("Skipping deletion of ticket {} - channel deletion failed",
                        ticket.getFormattedTicketId());
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to delete old ticket {}", ticket.getFormattedTicketId(), e);
                failedCount++;
            }
        }

        log.info("Ticket cleanup complete: {} deleted, {} failed", deletedCount, failedCount);
    }

    /**
     * Evaluate all open tickets and send reminder posts when a threshold is met.
     * Thresholds are evaluated in ascending order to allow escalating reminders.
     */
    public void sendReminders() {
        if (!config.isRemindersEnabled() || config.getReminderThresholds().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Use the optimized repository method that queries MongoDB directly
        List<Ticket> openTickets = ticketRepository.findOpenTickets();

        if (openTickets.isEmpty()) {
            return;
        }

        // Deduplicate by ticket number to avoid processing duplicates
        Map<Integer, Ticket> uniqueTickets = new LinkedHashMap<>();
        for (Ticket ticket : openTickets) {
            uniqueTickets.putIfAbsent(ticket.getTicketNumber(), ticket);
        }

        // Sort thresholds by hours ascending for proper escalation
        List<TicketReminderThreshold> sortedThresholds = config.getReminderThresholds().stream()
            .sorted(Comparator.comparingInt(TicketReminderThreshold::getHoursWithoutResponse))
            .toList();

        int remindersSent = 0;

        for (Ticket ticket : uniqueTickets.values()) {
            long hoursSinceUpdate = Math.round((now - ticket.getUpdatedAt()) / (double) TimeUnit.HOURS.toMillis(1));

            // Find the highest applicable threshold we haven't sent yet
            TicketReminderThreshold applicableThreshold = null;

            for (TicketReminderThreshold threshold : sortedThresholds) {
                if (hoursSinceUpdate >= threshold.getHoursWithoutResponse()) {
                    // Only consider this threshold if we haven't already sent it
                    if (threshold.getHoursWithoutResponse() > ticket.getLastReminderThresholdHours()) {
                        applicableThreshold = threshold;
                    }
                }
            }

            if (applicableThreshold == null) {
                continue;
            }

            // Send the reminder
            try {
                notificationService.sendReminderToChannel(ticket, applicableThreshold, hoursSinceUpdate);

                // Update tracking
                ticket.setLastReminderSent(now);
                ticket.setLastReminderThresholdHours(applicableThreshold.getHoursWithoutResponse());
                ticketRepository.saveToDatabase(ticket);

                // Metrics
                PrometheusMetrics.TICKET_REMINDERS_SENT.inc();

                remindersSent++;
                log.info("Sent {}h reminder for ticket {}",
                    applicableThreshold.getHoursWithoutResponse(), ticket.getFormattedTicketId());
            } catch (Exception e) {
                log.error("Failed to send reminder for ticket {}", ticket.getFormattedTicketId(), e);
            }
        }

        if (remindersSent > 0) {
            log.info("Sent {} ticket reminders", remindersSent);
        }
    }

    /**
     * Determine if the given channel is a ticket channel.
     *
     * @param channel channel candidate
     *
     * @return true if the channel is in the configured ticket category
     */
    public boolean isTicketChannel(TextChannel channel) {
        return discordService.isTicketChannel(channel);
    }

    /**
     * Refresh the control panel for a ticket.
     */
    public void refreshControlPanel(Ticket ticket) {
        discordService.refreshControlPanel(ticket);
    }

    /**
     * Delete all tickets, their channels, and reset the counter.
     * WARNING: This is a destructive operation for testing/development only.
     *
     * @return summary of deleted items
     */
    public String deleteAllTickets() {
        log.warn("Deleting all tickets - destructive operation initiated");

        // Get all tickets before deletion to find their channels
        List<Ticket> allTickets = ticketRepository.getAllDocuments();
        int channelsDeleted = 0;
        int channelsFailed = 0;

        // Delete all ticket channels
        for (Ticket ticket : allTickets) {
            if (ticket.getChannelId() != null) {
                if (discordService.deleteChannel(ticket)) {
                    channelsDeleted++;
                } else {
                    channelsFailed++;
                }
            }
        }

        // Delete all database records and reset counter
        long recordsDeleted = ticketRepository.deleteAllTicketsAndResetCounter();

        String summary = String.format(
            "Deleted %d database records, %d ticket channels (%d failed), and reset counter",
            recordsDeleted, channelsDeleted, channelsFailed
        );
        log.warn(summary);
        return summary;
    }

    private void pruneTicketsWithMissingChannels() {
        JDA jda = DiscordBotEnvironment.getBot().getJDA();
        if (jda == null) {
            return;
        }

        List<Ticket> openTickets = ticketRepository.findOpenTickets();

        // Deduplicate by ticket number to avoid processing duplicates
        Map<Integer, Ticket> uniqueTickets = new LinkedHashMap<>();
        for (Ticket ticket : openTickets) {
            uniqueTickets.putIfAbsent(ticket.getTicketNumber(), ticket);
        }

        int prunedCount = 0;

        for (Ticket ticket : uniqueTickets.values()) {
            if (ticket.getChannelId() != null && jda.getTextChannelById(ticket.getChannelId()) == null) {
                ticketRepository.removeFromChannelIndex(ticket.getChannelId());
                ticketRepository.deleteFromDatabase(String.valueOf(ticket.getTicketNumber()));
                log.info("Removed orphaned ticket {} referencing deleted channel {}", ticket.getFormattedTicketId(), ticket.getChannelId());
                prunedCount++;
            }
        }

        if (prunedCount > 0) {
            log.info("Pruned {} orphaned tickets with missing channels", prunedCount);
        }
    }

    /**
     * Archive overflow messages if the ticket has exceeded the storage limit.
     * Moves excess messages to the archive repository and clears them from the ticket.
     *
     * @param ticket the ticket to check for overflow
     */
    private void archiveOverflowMessages(Ticket ticket) {
        List<TicketMessage> overflow = ticket.getOverflowMessagesAndClear();
        if (overflow.isEmpty()) {
            return;
        }

        if (messageArchiveRepository != null) {
            try {
                messageArchiveRepository.archiveOverflowMessages(ticket.getTicketNumber(), overflow);
                log.info("Archived {} overflow messages for ticket {}",
                    overflow.size(), ticket.getFormattedTicketId());
            } catch (Exception e) {
                log.error("Failed to archive overflow messages for ticket {}",
                    ticket.getFormattedTicketId(), e);
                // Re-add messages to ticket on failure to prevent data loss
                for (TicketMessage msg : overflow) {
                    ticket.getMessages().add(0, msg);
                }
            }
        } else {
            log.warn("Message archive repository not available - {} overflow messages discarded for ticket {}",
                overflow.size(), ticket.getFormattedTicketId());
        }
    }

    /**
     * Download attachments into in-memory buffers for forwarding.
     * Downloads are performed in parallel for better performance.
     *
     * @param attachments list of attachments to download
     *
     * @return list of file uploads ready to send
     */
    private List<FileUpload> downloadAttachments(List<Message.Attachment> attachments) {
        if (attachments.isEmpty()) {
            return List.of();
        }

        // Download all attachments in parallel
        List<CompletableFuture<FileUpload>> futures = attachments.stream()
            .map(attachment -> CompletableFuture.supplyAsync(() -> {
                try {
                    byte[] data = attachment.getProxy().download().join().readAllBytes();
                    return FileUpload.fromData(data, attachment.getFileName());
                } catch (Exception e) {
                    log.error("Failed to download attachment: {}", attachment.getFileName(), e);
                    return null;
                }
            }))
            .toList();

        // Wait for all downloads to complete and filter out failures
        return futures.stream()
            .map(CompletableFuture::join)
            .filter(upload -> upload != null)
            .toList();
    }

    /**
     * Get statistics about the channel index.
     *
     * @return index statistics
     */
    public ChannelIndexStats getChannelIndexStats() {
        return ticketRepository.getChannelIndexStats();
    }
}