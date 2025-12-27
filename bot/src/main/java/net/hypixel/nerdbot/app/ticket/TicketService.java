package net.hypixel.nerdbot.app.ticket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketReminderThreshold;
import net.hypixel.nerdbot.discord.config.channel.TicketStatusConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketMessage;
import net.hypixel.nerdbot.discord.storage.database.repository.TicketRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Central service that handles ticket creation, storage, reminders, status management,
 * and Discord thread syncing for the guild ticketing system.
 */
@Slf4j
public class TicketService {

    private static final int MAX_MESSAGE_LENGTH = 2_000;
    private static volatile TicketService instance;
    @Getter
    private final TicketConfig config;
    @Getter
    private final TicketRepository ticketRepository;

    /**
     * Construct a new ticket service instance.
     *
     * @param config           ticket configuration backing this service
     * @param ticketRepository repository used for persistence
     */
    private TicketService(TicketConfig config, TicketRepository ticketRepository) {
        this.config = config;
        this.ticketRepository = ticketRepository;
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
                    instance = new TicketService(config, repository);
                }
            }
        }
        return instance;
    }

    /**
     * Get the configured forum channel used for all tickets.
     *
     * @return optional forum channel reference
     */
    public Optional<ForumChannel> getTicketChannel() {
        return ChannelCache.getTicketChannel();
    }

    /**
     * Ensure all required forum tags exist in the ticket channel and cache their IDs
     */
    public void ensureForumTagsExist() {
        Optional<ForumChannel> forumOpt = getTicketChannel();
        if (forumOpt.isEmpty()) {
            log.warn("Cannot create forum tags - ticket channel not found");
            return;
        }

        ForumChannel forum = forumOpt.get();
        List<ForumTag> existingTags = forum.getAvailableTags();
        List<String> existingTagNames = existingTags.stream()
            .map(ForumTag::getName)
            .map(String::toLowerCase)
            .toList();

        List<ForumTagData> tagsToCreate = new ArrayList<>();

        // Check status tags - create any that are missing
        for (TicketStatusConfig statusConfig : config.getStatuses()) {
            String statusTagName = statusConfig.getDisplayName();
            if (!existingTagNames.contains(statusTagName.toLowerCase())) {
                ForumTagData tagData = new ForumTagData(statusTagName);

                // Apply emoji if configured
                if (statusConfig.getEmoji() != null && !statusConfig.getEmoji().isEmpty()) {
                    try {
                        tagData.setEmoji(Emoji.fromUnicode(statusConfig.getEmoji()));
                    } catch (Exception e) {
                        log.warn("Failed to parse emoji '{}' for status {}: {}",
                            statusConfig.getEmoji(), statusConfig.getId(), e.getMessage());
                    }
                }

                tagsToCreate.add(tagData);
                log.info("Will create status tag: {} for status {}", statusTagName, statusConfig.getId());
            }
        }

        // Check category tags
        for (TicketConfig.TicketCategory category : config.getCategories()) {
            if (!existingTagNames.contains(category.getDisplayName().toLowerCase())) {
                tagsToCreate.add(new ForumTagData(category.getDisplayName()));
                log.info("Will create category tag: {}", category.getDisplayName());
            }
        }

        if (!tagsToCreate.isEmpty()) {
            List<ForumTagData> allTags = new ArrayList<>();
            // Keep existing tags
            for (ForumTag existing : existingTags) {
                allTags.add(ForumTagData.from(existing));
            }
            // Add new tags
            allTags.addAll(tagsToCreate);

            try {
                forum.getManager().setAvailableTags(allTags).complete();
                log.info("Created {} new forum tags", tagsToCreate.size());
            } catch (Exception e) {
                log.error("Failed to create forum tags", e);
            }
        } else {
            log.info("All required forum tags already exist");
        }

        cacheForumTagIds();
    }

    /**
     * Cache all forum tag IDs from the ticket channel
     */
    private void cacheForumTagIds() {
        ChannelCache.refreshTicketTagCache();
        log.info("Cached ticket forum tag IDs");
    }

    /**
     * Sync forum tags for all existing tickets.
     * This ensures tickets with missing or incorrect status tags are updated.
     */
    public void syncAllTicketTags() {
        List<Ticket> allTickets = ticketRepository.getAll();
        if (allTickets.isEmpty()) {
            log.info("No tickets to sync");
            return;
        }

        log.info("Syncing forum tags for {} tickets...", allTickets.size());
        int synced = 0;
        int failed = 0;

        for (Ticket ticket : allTickets) {
            try {
                updateThreadTags(ticket);
                synced++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to sync tags for ticket {}: {}", ticket.getFormattedTicketId(), e.getMessage());
            }
        }

        log.info("Ticket tag sync complete: {} synced, {} failed", synced, failed);
    }

    /**
     * Get the display name for a status ID
     *
     * @param statusId the status ID
     *
     * @return the display name, or the ID if not found
     */
    public String getStatusDisplayName(String statusId) {
        return config.getStatusDisplayName(statusId);
    }

    /**
     * Determine if the provided status ID maps to a closed state.
     *
     * @param statusId status identifier
     *
     * @return true when the status is marked as closed
     */
    public boolean isClosedStatus(String statusId) {
        return config.isClosedStatus(statusId);
    }

    /**
     * Get the ID of the status used for newly opened tickets.
     *
     * @return default open status ID, falling back to {@code open}
     */
    public String getDefaultOpenStatusId() {
        TicketStatusConfig defaultStatus = config.getDefaultOpenStatus();
        return defaultStatus != null ? defaultStatus.getId() : "open";
    }

    /**
     * Look up the forum tag representing the provided status ID.
     *
     * @param statusId status identifier (null implies default open)
     *
     * @return matching forum tag if present
     */
    public Optional<ForumTag> getStatusTag(String statusId) {
        String effectiveStatusId = statusId != null ? statusId : getDefaultOpenStatusId();
        String tagName = getStatusDisplayName(effectiveStatusId);

        Optional<ForumTag> cachedTag = ChannelCache.getTicketTagByName(tagName);
        if (cachedTag.isPresent()) {
            return cachedTag;
        }

        return getTicketChannel().flatMap(forum -> getTagByName(forum, tagName));
    }

    /**
     * Create a ticket thread on behalf of the given user.
     *
     * @param user        ticket owner
     * @param categoryId  configured category identifier
     * @param description problem description supplied by the user
     *
     * @return persisted ticket instance
     */
    public Ticket createTicket(User user, String categoryId, String description) {
        int openTickets = ticketRepository.countOpenTicketsByUser(user.getId());
        if (openTickets >= config.getMaxOpenTicketsPerUser()) {
            throw new IllegalStateException("You have reached the maximum number of open tickets (" + config.getMaxOpenTicketsPerUser() + ")");
        }

        Optional<ForumChannel> forumOpt = getTicketChannel();
        if (forumOpt.isEmpty()) {
            throw new IllegalStateException("Ticket channel not found");
        }

        int ticketNumber = ticketRepository.getNextTicketNumber();
        Ticket ticket = new Ticket(ticketNumber, user.getId());
        ticket.setCategoryId(categoryId);

        ForumChannel forum = forumOpt.get();

        String categoryName = config.getCategories().stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .map(TicketConfig.TicketCategory::getDisplayName)
            .orElse(categoryId);

        String threadName = generateThreadName(ticket, user, categoryName);
        String header = generateInitialPost(user, ticket, categoryName);

        List<ForumTag> tags = new ArrayList<>();
        getStatusTag(getDefaultOpenStatusId()).ifPresent(tags::add);
        getCategoryTag(forum, categoryId).ifPresent(tags::add);

        // Calculate how much description fits in first message
        int remainingSpace = MAX_MESSAGE_LENGTH - header.length();
        String firstMessageContent;
        String remainingDescription = null;

        if (description.length() <= remainingSpace) {
            firstMessageContent = header + description;
        } else {
            firstMessageContent = header + description.substring(0, remainingSpace);
            remainingDescription = description.substring(remainingSpace);
        }

        ThreadChannel thread = forum.createForumPost(threadName, MessageCreateData.fromContent(firstMessageContent))
            .setTags(tags)
            .complete()
            .getThreadChannel();

        // Send remaining description in follow-up messages
        if (remainingDescription != null) {
            List<String> chunks = splitMessage(remainingDescription);
            for (String chunk : chunks) {
                thread.sendMessage(chunk).complete();
            }
        }

        thread.getManager().setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS).queue();

        ticket.setThreadId(thread.getId());
        ticket.setForumChannelId(forum.getId());

        TicketMessage initMessage = new TicketMessage(user.getId(), user.getEffectiveName(), description, false);
        ticket.addMessage(initMessage);

        ticketRepository.cacheObject(ticket);
        ticketRepository.saveToDatabase(ticket);

        addTicketRoleMembersToThread(thread);
        pingTicketRole(thread);

        log.info("Created ticket {} for user {} (Thread: {})", ticket.getFormattedTicketId(), user.getId(), thread.getId());

        return ticket;
    }

    /**
     * Handle a message posted directly into the ticket thread.
     *
     * @param author      message author
     * @param thread      target ticket thread
     * @param content     textual content from the message
     * @param attachments uploaded attachments on the message
     * @param isStaff     whether the author is staff (affects auto-status rules)
     */
    public void handleTicketMessage(User author, ThreadChannel thread, String content, List<Message.Attachment> attachments, boolean isStaff) {
        if (content.startsWith("?")) {
            log.info("{} sent a hidden message in ticket thread {}", author.getName(), thread.getId());
            return;
        }

        Optional<Ticket> ticketOpt = ticketRepository.findByThreadId(thread.getId());
        if (ticketOpt.isEmpty()) {
            return;
        }

        Ticket ticket = ticketOpt.get();

        TicketMessage msg = new TicketMessage();
        msg.setMessageId(thread.getLatestMessageId());
        msg.setAuthorId(author.getId());
        msg.setAuthorName(author.getEffectiveName());
        msg.setContent(content);
        msg.setAttachmentUrls(attachments.stream().map(Message.Attachment::getUrl).toList());
        msg.setTimestamp(System.currentTimeMillis());
        msg.setStaff(isStaff);

        ticket.addMessage(msg);

        // Auto-status on staff reply
        if (isStaff && config.getStaffReplyStatus() != null) {
            String targetStatus = config.getStaffReplyStatus();
            if (!ticket.getStatusId().equals(targetStatus) && !isClosedStatus(ticket.getStatusId())) {
                String oldStatus = ticket.getStatusId();
                ticket.setStatusId(targetStatus);
                ticket.resetReminderTracking();
                updateThreadTags(ticket);
                log.info("Auto-updated ticket {} status from {} to {} after staff reply",
                    ticket.getFormattedTicketId(), oldStatus, targetStatus);
            }
        }

        ticketRepository.saveToDatabase(ticket);

        if (isStaff) {
            forwardToUser(ticket, author, content, attachments);
        }
    }

    /**
     * Handle a DM reply from the ticket owner which should be mirrored to the thread.
     *
     * @param user        ticket owner replying over DM
     * @param threadId    thread to mirror the reply into
     * @param content     message content
     * @param attachments uploaded attachments accompanying the DM
     */
    public void handleUserReply(User user, String threadId, String content, List<Message.Attachment> attachments) {
        Optional<Ticket> ticketOpt = ticketRepository.findByThreadId(threadId);
        if (ticketOpt.isEmpty()) {
            return;
        }

        Ticket ticket = ticketOpt.get();

        if (!ticket.getOwnerId().equals(user.getId())) {
            return;
        }

        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(threadId);
        if (thread == null) {
            return;
        }

        String prefix = "**" + user.getEffectiveName() + ":** ";
        int maxContentLength = MAX_MESSAGE_LENGTH - prefix.length();

        if (content.length() <= maxContentLength) {
            thread.sendMessage(prefix + content).queue();
        } else {
            // First message with prefix
            thread.sendMessage(prefix + content.substring(0, maxContentLength)).queue();
            // Remaining content split across messages
            String remaining = content.substring(maxContentLength);
            List<String> chunks = splitMessage(remaining);
            for (String chunk : chunks) {
                thread.sendMessage(chunk).queue();
            }
        }

        // Forward attachments to thread
        if (!attachments.isEmpty()) {
            List<FileUpload> uploads = downloadAttachments(attachments);
            if (!uploads.isEmpty()) {
                thread.sendFiles(uploads).queue();
            }
        }

        TicketMessage ticketMessage = new TicketMessage(user.getId(), user.getEffectiveName(), content, false);
        ticketMessage.setAttachmentUrls(attachments.stream().map(Message.Attachment::getUrl).toList());
        ticket.addMessage(ticketMessage);

        // Auto-status on user reply
        if (config.getUserReplyStatus() != null) {
            String targetStatus = config.getUserReplyStatus();
            if (!ticket.getStatusId().equals(targetStatus) && !isClosedStatus(ticket.getStatusId())) {
                String oldStatus = ticket.getStatusId();
                ticket.setStatusId(targetStatus);
                ticket.resetReminderTracking();
                updateThreadTags(ticket);
                log.info("Auto-updated ticket {} status from {} to {} after user reply",
                    ticket.getFormattedTicketId(), oldStatus, targetStatus);
            }
        }

        ticketRepository.saveToDatabase(ticket);

        log.info("User {} replied to ticket {} via DM", user.getId(), ticket.getFormattedTicketId());
    }

    /**
     * Persist a manual status change performed by staff.
     *
     * @param ticket      ticket being updated
     * @param newStatusId target status ID
     * @param changedBy   staff member making the change
     */
    public void updateStatus(Ticket ticket, String newStatusId, User changedBy) {
        String oldStatusId = ticket.getStatusId();
        ticket.setStatusId(newStatusId);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        updateThreadTags(ticket);
        ticketRepository.saveToDatabase(ticket);

        log.info("Ticket {} status changed from {} to {} by {}",
            ticket.getFormattedTicketId(), oldStatusId, newStatusId, changedBy.getId());
    }

    /**
     * Close a ticket and archive its Discord thread.
     *
     * @param ticket   ticket being closed
     * @param closedBy staff user performing the closure
     * @param reason   optional reason to display to staff/end user
     */
    public void closeTicket(Ticket ticket, User closedBy, String reason) {
        // Find the first closed status
        String closedStatusId = config.getClosedStatuses().stream()
            .findFirst()
            .map(TicketStatusConfig::getId)
            .orElse("closed");

        ticket.setStatusId(closedStatusId);
        ticket.setClosedAt(System.currentTimeMillis());
        ticket.setClosedById(closedBy.getId());
        ticket.setCloseReason(reason);

        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());

        if (thread != null) {
            updateThreadTags(ticket);

            if (config.isUploadTranscriptOnClose()) {
                String transcript = TicketTranscriptGenerator.generate(ticket, config);
                String reasonText = reason != null ? reason : "No reason provided";
                thread.sendMessage("**Ticket Closed** by " + closedBy.getAsMention() + "\n**Reason:** " + reasonText)
                    .addFiles(FileUpload.fromData(
                        transcript.getBytes(StandardCharsets.UTF_8),
                        "transcript-" + ticket.getFormattedTicketId().replace("#", "") + ".txt"))
                    .queue();
            }

            thread.getManager()
                .setArchived(true)
                .setLocked(true)
                .queue();
        }

        ticketRepository.saveToDatabase(ticket);
        notifyTicketClosed(ticket, closedBy, reason);

        // Clear conversation state so user doesn't try to reply to closed ticket
        TicketConversationManager.clearConversation(ticket.getOwnerId());

        log.info("Ticket {} closed by {}", ticket.getFormattedTicketId(), closedBy.getId());
    }

    /**
     * Reopen a closed ticket
     */
    public void reopenTicket(Ticket ticket, User reopenedBy, String reason) {
        if (!isClosedStatus(ticket.getStatusId())) {
            throw new IllegalStateException("Ticket is not closed");
        }

        ticket.setStatusId(getDefaultOpenStatusId());
        ticket.setClosedAt(-1);
        ticket.setClosedById(null);
        ticket.setCloseReason(null);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());

        if (thread != null) {
            thread.getManager()
                .setArchived(false)
                .setLocked(false)
                .queue(success -> {
                    // Update tags after unarchiving
                    updateThreadTags(ticket);

                    String reasonText = reason != null ? reason : "No reason provided";
                    thread.sendMessage("**Ticket Reopened** by " + reopenedBy.getAsMention() + "\n**Reason:** " + reasonText).queue();
                });
        }

        ticketRepository.saveToDatabase(ticket);

        // Notify the ticket owner
        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        if (owner != null) {
            owner.openPrivateChannel()
                .flatMap(ch -> ch.sendMessage(String.format("""
                                                            Your ticket **%s** has been reopened by %s.
                                                            
                                                            You can continue the conversation by replying here.
                                                            """, ticket.getFormattedTicketId(), reopenedBy.getEffectiveName())))
                .queue();
        }

        log.info("Ticket {} reopened by {}", ticket.getFormattedTicketId(), reopenedBy.getId());
    }

    /**
     * Claim a ticket for the supplied staff member and update thread metadata.
     *
     * @param ticket ticket being claimed
     * @param staff  staff member taking ownership
     */
    public void claimTicket(Ticket ticket, User staff) {
        ticket.setClaimedById(staff.getId());
        ticket.setStatusId("in_progress");
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticket.resetReminderTracking();

        updateThreadTags(ticket);
        ticketRepository.saveToDatabase(ticket);

        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());
        if (thread != null) {
            thread.sendMessage("**Ticket claimed** by " + staff.getAsMention()).queue();
        }

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

        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());
        if (thread != null) {
            String message = previousClaimant != null
                ? "**Ticket transferred** from <@" + previousClaimant + "> to " + newStaff.getAsMention() + " by " + transferredBy.getAsMention()
                : "**Ticket assigned** to " + newStaff.getAsMention() + " by " + transferredBy.getAsMention();
            thread.sendMessage(message).queue();
        }

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
        String autoCloseStatusId = config.getAutoCloseStatusId();

        ticketRepository.getAll().stream()
            .filter(ticket -> ticket.getStatusId().equals(autoCloseStatusId))
            .filter(ticket -> ticket.getUpdatedAt() < cutoffTime)
            .forEach(ticket -> {
                try {
                    // Use bot user for auto-close
                    User botUser = DiscordBotEnvironment.getBot().getJDA().getSelfUser();
                    closeTicket(ticket, botUser, config.getAutoCloseMessage());
                    log.info("Auto-closed stale ticket {} (in {} status for {} days)",
                        ticket.getFormattedTicketId(), autoCloseStatusId, config.getAutoCloseDays());
                } catch (Exception e) {
                    log.error("Failed to auto-close ticket {}", ticket.getFormattedTicketId(), e);
                }
            });
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

        // Get all open tickets
        List<Ticket> openTickets = ticketRepository.getAll().stream()
            .filter(Ticket::isOpen)
            .toList();

        if (openTickets.isEmpty()) {
            return;
        }

        // Sort thresholds by hours ascending for proper escalation
        List<TicketReminderThreshold> sortedThresholds = config.getReminderThresholds().stream()
            .sorted(Comparator.comparingInt(TicketReminderThreshold::getHoursWithoutResponse))
            .toList();

        int remindersSent = 0;

        for (Ticket ticket : openTickets) {
            long hoursSinceUpdate = (now - ticket.getUpdatedAt()) / TimeUnit.HOURS.toMillis(1);

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
                sendReminderToThread(ticket, applicableThreshold);

                // Update tracking
                ticket.setLastReminderSent(now);
                ticket.setLastReminderThresholdHours(applicableThreshold.getHoursWithoutResponse());
                ticketRepository.saveToDatabase(ticket);

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
     * Post the reminder message into the ticket thread, optionally pinging staff.
     *
     * @param ticket    ticket being reminded
     * @param threshold reminder configuration triggering this send
     */
    private void sendReminderToThread(Ticket ticket, TicketReminderThreshold threshold) {
        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());
        if (thread == null) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(":bell: **Reminder:** ").append(threshold.getMessage());

        if (threshold.isPingStaff() && config.getTicketRoleId() != null && !config.getTicketRoleId().isEmpty()) {
            message.append("\n<@&").append(config.getTicketRoleId()).append(">");
        }

        thread.sendMessage(message.toString()).queue();
    }

    /**
     * Determine if the given thread belongs to the configured ticket forum.
     *
     * @param thread thread candidate
     *
     * @return true if the thread is inside the configured ticket forum
     */
    public boolean isTicketThread(ThreadChannel thread) {
        if (!(thread.getParentChannel() instanceof ForumChannel)) {
            return false;
        }

        Optional<ForumChannel> ticketChannel = getTicketChannel();
        return ticketChannel.isPresent() && ticketChannel.get().getId().equals(thread.getParentChannel().getId());
    }

    /**
     * Build a human-readable thread name for the new ticket.
     *
     * @param ticket       ticket being created
     * @param user         ticket owner
     * @param categoryName human-readable category display name
     *
     * @return formatted thread title
     */
    private String generateThreadName(Ticket ticket, User user, String categoryName) {
        return ticket.getFormattedTicketId() + " - " + categoryName + " - " + user.getName();
    }

    /**
     * Generate the initial message body used when creating the forum thread.
     *
     * @param user         ticket owner
     * @param ticket       ticket being created
     * @param categoryName chosen category display name
     *
     * @return initial post contents
     */
    private String generateInitialPost(User user, Ticket ticket, String categoryName) {
        return String.format("""
                             **New Ticket %s**
                             
                             **User:** %s
                             **User ID:** %s
                             **Category:** %s
                             **Created:** <t:%d:F>
                             
                             **Description:**
                             """,
            ticket.getFormattedTicketId(),
            user.getAsMention(),
            user.getId(),
            categoryName,
            ticket.getCreatedAt() / 1_000
        );
    }

    /**
     * Split content into chunks that respect the Discord character limit.
     *
     * @param content long text message
     *
     * @return list of chunks at most {@link #MAX_MESSAGE_LENGTH} each
     */
    private List<String> splitMessage(String content) {
        List<String> messages = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + TicketService.MAX_MESSAGE_LENGTH, content.length());
            messages.add(content.substring(start, end));
            start = end;
        }
        return messages;
    }

    /**
     * Download the provided attachments into in-memory buffers for forwarding.
     *
     * @param attachments attachments to download
     *
     * @return list of {@link FileUpload} objects ready to send
     */
    private List<FileUpload> downloadAttachments(List<Message.Attachment> attachments) {
        List<FileUpload> uploads = new ArrayList<>();
        for (Message.Attachment attachment : attachments) {
            try {
                byte[] data = attachment.getProxy().download().get().readAllBytes();
                uploads.add(FileUpload.fromData(data, attachment.getFileName()));
            } catch (Exception e) {
                log.error("Failed to download attachment: {}", attachment.getFileName(), e);
            }
        }
        return uploads;
    }

    /**
     * Update the forum tags on the ticket thread to match its status and category.
     *
     * @param ticket ticket whose thread tags should be refreshed
     */
    private void updateThreadTags(Ticket ticket) {
        ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(ticket.getThreadId());
        if (thread == null || !(thread.getParentChannel() instanceof ForumChannel forum)) {
            return;
        }

        List<ForumTag> tags = new ArrayList<>();

        // Get status tag using cached IDs
        getStatusTag(ticket.getStatusId()).ifPresent(tags::add);
        getCategoryTag(forum, ticket.getCategoryId()).ifPresent(tags::add);

        thread.getManager().setAppliedTags(tags).queue();
    }

    /**
     * Find a tag in the provided forum by case-insensitive name.
     *
     * @param forum forum channel to search
     * @param name  tag name to match
     *
     * @return optional {@link ForumTag}
     */
    private Optional<ForumTag> getTagByName(ForumChannel forum, String name) {
        if (name == null) {
            return Optional.empty();
        }

        return forum.getAvailableTags().stream()
            .filter(t -> t.getName().equalsIgnoreCase(name))
            .findFirst();
    }

    /**
     * Find the forum tag that corresponds to the configured ticket category ID.
     *
     * @param forum      ticket forum channel
     * @param categoryId category identifier from config
     *
     * @return optional matching tag
     */
    private Optional<ForumTag> getCategoryTag(ForumChannel forum, String categoryId) {
        return config.getCategories().stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .flatMap(c -> {
                Optional<ForumTag> cachedTag = ChannelCache.getTicketTagByName(c.getDisplayName());
                if (cachedTag.isPresent()) {
                    return cachedTag;
                }

                return getTagByName(forum, c.getDisplayName());
            });
    }

    /**
     * Invite members with the configured ticket role into the thread.
     *
     * @param thread ticket thread to populate
     */
    private void addTicketRoleMembersToThread(ThreadChannel thread) {
        String ticketRoleId = config.getTicketRoleId();

        if (ticketRoleId != null && !ticketRoleId.isEmpty()) {
            RoleManager.getRoleById(ticketRoleId).ifPresent(role ->
                thread.getGuild().getMembersWithRoles(role).forEach(member ->
                    thread.addThreadMember(member).queue(
                        success -> log.debug("Added {} to ticket thread {}", member.getEffectiveName(), thread.getName()),
                        error -> log.error("Failed to add {} to ticket thread {}", member.getEffectiveName(), thread.getName(), error)
                    )
                )
            );
        }
    }

    /**
     * Optionally ping the ticket role after a thread is created.
     *
     * @param thread newly created ticket thread
     */
    private void pingTicketRole(ThreadChannel thread) {
        String ticketRoleId = config.getTicketRoleId();
        if (ticketRoleId != null && !ticketRoleId.isEmpty()) {
            thread.sendMessage("<@&" + ticketRoleId + ">").queue();
        }
    }

    /**
     * Forward a staff reply from the thread to the ticket owner's DM channel.
     *
     * @param ticket      ticket being replied to
     * @param staff       staff member authoring the reply
     * @param content     reply content
     * @param attachments any attachments included by staff
     */
    private void forwardToUser(Ticket ticket, User staff, String content, List<Message.Attachment> attachments) {
        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        if (owner == null) {
            return;
        }

        String prefix = String.format("**[Ticket %s] %s:** ", ticket.getFormattedTicketId(), staff.getEffectiveName());
        int maxContentLength = MAX_MESSAGE_LENGTH - prefix.length();

        owner.openPrivateChannel().queue(channel -> {
            if (content.length() <= maxContentLength) {
                channel.sendMessage(prefix + content).queue();
            } else {
                // First message with prefix
                channel.sendMessage(prefix + content.substring(0, maxContentLength)).queue();
                // Remaining content split across messages
                String remaining = content.substring(maxContentLength);
                List<String> chunks = splitMessage(remaining);
                for (String chunk : chunks) {
                    channel.sendMessage(chunk).queue();
                }
            }

            // Forward attachments to user
            if (!attachments.isEmpty()) {
                List<FileUpload> uploads = downloadAttachments(attachments);
                if (!uploads.isEmpty()) {
                    channel.sendFiles(uploads).queue();
                }
            }

            log.debug("Forwarded staff message to user {}", owner.getId());
        }, error -> log.error("Failed to forward message to user {}", owner.getId(), error));
    }

    /**
     * Notify the ticket owner via DM that their ticket was closed.
     *
     * @param ticket   ticket that was closed
     * @param closedBy staff user who closed it
     * @param reason   optional closure reason
     */
    private void notifyTicketClosed(Ticket ticket, User closedBy, String reason) {
        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        if (owner == null) {
            return;
        }

        String message = String.format("""
                                       Your ticket **%s** has been closed by %s.
                                       
                                       **Reason:** %s
                                       
                                       If you need further assistance, feel free to open a new ticket by sending me a message.
                                       """,
            ticket.getFormattedTicketId(),
            closedBy.getEffectiveName(),
            reason != null ? reason : "No reason provided"
        );

        owner.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage(message))
            .queue(
                success -> log.debug("Notified user {} of ticket close", owner.getId()),
                error -> log.error("Failed to notify user {} of ticket close", owner.getId(), error)
            );
    }
}