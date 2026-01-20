package net.hypixel.nerdbot.app.ticket.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.hypixel.nerdbot.app.role.RoleManager;
import net.hypixel.nerdbot.app.ticket.TicketTranscriptGenerator;
import net.hypixel.nerdbot.app.ticket.control.BuiltInTicketActions;
import net.hypixel.nerdbot.app.ticket.control.TicketAction;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketStatusTransition;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.storage.database.repository.TicketRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Handles Discord-specific ticket operations (channels, permissions, UI components).
 */
@Slf4j
public class TicketDiscordService {

    private static final int MAX_MESSAGE_LENGTH = 2_000;

    private final TicketConfig config;
    private final TicketRepository ticketRepository;

    public TicketDiscordService(TicketConfig config, TicketRepository ticketRepository) {
        this.config = config;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Get the configured ticket category for creating new ticket channels.
     */
    public Optional<Category> getTicketCategory() {
        String categoryId = config.getTicketCategoryId();
        if (categoryId == null || categoryId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DiscordBotEnvironment.getBot().getJDA().getCategoryById(categoryId));
    }

    /**
     * Get the configured closed ticket category for moving closed tickets.
     */
    public Optional<Category> getClosedTicketCategory() {
        String categoryId = config.getClosedTicketCategoryId();
        if (categoryId == null || categoryId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DiscordBotEnvironment.getBot().getJDA().getCategoryById(categoryId));
    }

    /**
     * Create a private text channel for a new ticket.
     *
     * @param ticket       the ticket being created
     * @param user         the ticket owner
     * @param categoryName the display name of the ticket category
     * @param description  the ticket description
     *
     * @return the created channel, or empty if creation failed
     */
    public Optional<TextChannel> createTicketChannel(Ticket ticket, User user, String categoryName, String description) {
        Optional<Category> categoryOpt = getTicketCategory();
        if (categoryOpt.isEmpty()) {
            log.error("Cannot create ticket channel - ticket category not found");
            return Optional.empty();
        }

        Category category = categoryOpt.get();
        Guild guild = category.getGuild();
        String channelName = generateChannelName(ticket, user);
        String header = generateInitialPost(user, ticket, categoryName);

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

        try {
            // Create the private text channel with permission overwrites
            TextChannel channel = category.createTextChannel(channelName)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(guild.getSelfMember(), EnumSet.of(
                    Permission.VIEW_CHANNEL,
                    Permission.MESSAGE_SEND,
                    Permission.MESSAGE_HISTORY,
                    Permission.MESSAGE_MANAGE,
                    Permission.MANAGE_CHANNEL
                ), null)
                .complete();

            // Add ticket owner permissions
            Member ownerMember = guild.getMemberById(user.getId());
            if (ownerMember != null) {
                channel.upsertPermissionOverride(ownerMember)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES)
                    .complete();
            }

            // Add ticket role permissions
            addTicketRolePermissions(channel);

            // Send initial message
            Message initialMessage = channel.sendMessage(MessageCreateData.fromContent(firstMessageContent)).complete();

            // Send remaining description in follow-up messages
            if (remainingDescription != null) {
                List<String> chunks = splitMessage(remainingDescription);
                for (String chunk : chunks) {
                    channel.sendMessage(chunk).complete();
                }
            }

            // Set up control panel
            ticket.setButtonControllerMessageId(initialMessage.getId());
            initialMessage.editMessageComponents(buildControlPanelComponents(ticket).toArray(ActionRow[]::new)).queue(
                null,
                error -> log.error("Failed to add control panel to channel {}", channel.getId(), error)
            );

            // Ping ticket role
            pingTicketRole(channel);

            // Create internal staff thread
            createInternalThread(channel, ticket);

            return Optional.of(channel);
        } catch (Exception e) {
            log.error("Failed to create ticket channel", e);
            return Optional.empty();
        }
    }

    /**
     * Update the channel name to reflect ticket status.
     */
    public void updateChannelName(Ticket ticket) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            return;
        }

        User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
        String userName = owner != null ? sanitizeChannelName(owner.getName()) : "unknown";

        String newName = config.getTicketChannelPrefix() + String.format("%04d", ticket.getTicketNumber()) + "-" + userName;

        // Limit channel name to 100 characters (Discord limit)
        if (newName.length() > 100) {
            newName = newName.substring(0, 100);
        }

        channel.getManager().setName(newName).queue(
            null,
            error -> log.error("Failed to update channel name for ticket {}", ticket.getChannelId(), error)
        );
    }

    /**
     * Archive a ticket channel by removing user permissions and optionally moving to closed category.
     */
    public void archiveChannel(Ticket ticket) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            return;
        }

        // Remove ticket owner's permissions (they can no longer see or send messages)
        Member ownerMember = channel.getGuild().getMemberById(ticket.getOwnerId());
        if (ownerMember != null) {
            channel.upsertPermissionOverride(ownerMember)
                .deny(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
                .queue(
                    null,
                    error -> log.error("Failed to remove owner permissions for ticket {}", ticket.getChannelId(), error)
                );
        }

        // Move to closed category if configured, otherwise just update name
        Optional<Category> closedCategory = getClosedTicketCategory();
        if (closedCategory.isPresent()) {
            channel.getManager()
                .setParent(closedCategory.get())
                .queue(
                    null,
                    error -> log.error("Failed to move ticket {} to closed category", ticket.getChannelId(), error)
                );
        }

        // Update channel name to show closed status
        updateChannelName(ticket);
    }

    /**
     * Delete a ticket channel entirely.
     * Used for cleaning up old closed tickets.
     *
     * @param ticket the ticket whose channel should be deleted
     *
     * @return true if the channel was deleted successfully or didn't exist
     */
    public boolean deleteChannel(Ticket ticket) {
        if (ticket.getChannelId() == null) {
            return true;
        }

        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            log.debug("Channel {} for ticket {} already deleted or not found",
                ticket.getChannelId(), ticket.getFormattedTicketId());
            return true;
        }

        try {
            channel.delete().complete();
            log.info("Deleted channel {} for ticket {}", ticket.getChannelId(), ticket.getFormattedTicketId());
            return true;
        } catch (net.dv8tion.jda.api.exceptions.ErrorResponseException e) {
            // Unknown Channel (10003) means the channel was already deleted
            if (e.getErrorCode() == 10_003) {
                log.info("Channel {} for ticket {} was already deleted", ticket.getChannelId(), ticket.getFormattedTicketId());
                return true;
            }
            log.error("Failed to delete channel {} for ticket {}", ticket.getChannelId(), ticket.getFormattedTicketId(), e);
            return false;
        } catch (Exception e) {
            log.error("Failed to delete channel {} for ticket {}", ticket.getChannelId(), ticket.getFormattedTicketId(), e);
            return false;
        }
    }

    /**
     * Unarchive a ticket channel by restoring user permissions and moving back to open category.
     */
    public void unarchiveChannel(Ticket ticket, Runnable onSuccess) {
        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        // Restore ticket owner's permissions (clear denies and grant access)
        Member ownerMember = channel.getGuild().getMemberById(ticket.getOwnerId());
        if (ownerMember != null) {
            channel.upsertPermissionOverride(ownerMember)
                .clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) // Clear any denies first
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES)
                .queue(
                    null,
                    error -> log.error("Failed to restore owner permissions for ticket {}", ticket.getChannelId(), error)
                );
        }

        // Move back to open ticket category
        Optional<Category> openCategory = getTicketCategory();
        if (openCategory.isPresent() && !channel.getParentCategoryId().equals(openCategory.get().getId())) {
            channel.getManager()
                .setParent(openCategory.get())
                .queue(
                    success -> {
                        updateChannelName(ticket);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    },
                    error -> {
                        log.error("Failed to move ticket {} back to open category", ticket.getChannelId(), error);
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }
                );
        } else {
            updateChannelName(ticket);
            if (onSuccess != null) {
                onSuccess.run();
            }
        }
    }

    /**
     * Upload a transcript file to the ticket channel and optionally to the transcript archive channel.
     */
    public void uploadTranscript(Ticket ticket, User closedBy, String reason) {
        String transcript = TicketTranscriptGenerator.generate(ticket, config);
        String reasonText = reason != null ? reason : "No reason provided";
        String closedByText = closedBy != null ? closedBy.getAsMention() : "System";
        String fileName = "transcript-" + ticket.getFormattedTicketId().replace("#", "") + ".txt";
        byte[] transcriptBytes = transcript.getBytes(StandardCharsets.UTF_8);

        // Upload to the ticket channel
        TextChannel ticketChannel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (ticketChannel != null) {
            ticketChannel.sendMessage("**Ticket Closed** by " + closedByText + "\n**Reason:** " + reasonText)
                .addFiles(FileUpload.fromData(transcriptBytes, fileName))
                .queue(
                    null,
                    error -> log.error("Failed to upload transcript to ticket channel {}", ticket.getChannelId(), error)
                );
        }

        // Upload to the transcript archive channel if configured
        if (config.isTranscriptChannelEnabled()) {
            TextChannel transcriptChannel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(config.getTranscriptChannelId());
            if (transcriptChannel != null) {
                User owner = DiscordBotEnvironment.getBot().getJDA().getUserById(ticket.getOwnerId());
                String ownerText = owner != null ? owner.getAsMention() : "Unknown (" + ticket.getOwnerId() + ")";
                String categoryName = config.getCategoryDisplayName(ticket.getTicketCategoryId());

                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Ticket Closed: " + ticket.getFormattedTicketId())
                    .addField("Owner", ownerText, true)
                    .addField("Category", categoryName, true)
                    .addField("Closed By", closedByText, true)
                    .addField("Reason", reasonText, false)
                    .setColor(0xED4245)
                    .setTimestamp(java.time.Instant.now());

                transcriptChannel.sendMessageEmbeds(embed.build())
                    .addFiles(FileUpload.fromData(transcriptBytes, fileName))
                    .queue(
                        null,
                        error -> log.error("Failed to upload transcript to archive channel {}", config.getTranscriptChannelId(), error)
                    );
            } else {
                log.warn("Transcript channel {} not found", config.getTranscriptChannelId());
            }
        }
    }

    /**
     * Refresh the control panel buttons on a ticket channel.
     */
    public void refreshControlPanel(Ticket ticket) {
        refreshControlPanel(ticket, null);
    }

    /**
     * Refresh the control panel buttons on a ticket channel with a callback.
     */
    public void refreshControlPanel(Ticket ticket, Runnable afterUpdate) {
        if (ticket == null || ticket.getChannelId() == null) {
            if (afterUpdate != null) {
                afterUpdate.run();
            }
            return;
        }

        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            log.debug("Cannot refresh ticket controls for {} - channel missing", ticket.getFormattedTicketId());
            if (afterUpdate != null) {
                afterUpdate.run();
            }
            return;
        }

        Runnable onComplete = () -> {
            if (afterUpdate != null) {
                afterUpdate.run();
            }
        };

        if (ticket.getButtonControllerMessageId() == null) {
            // Try to find the first message in the channel
            channel.getHistory().retrievePast(1)
                .queue(messages -> {
                        if (!messages.isEmpty()) {
                            Message firstMessage = messages.getFirst();
                            ticket.setButtonControllerMessageId(firstMessage.getId());
                            ticketRepository.saveToDatabase(ticket);
                            firstMessage.editMessageComponents(buildControlPanelComponents(ticket).toArray(ActionRow[]::new))
                                .queue(message -> onComplete.run(), failure -> {
                                    log.debug("Skipping control panel update for {}: {}", ticket.getFormattedTicketId(), failure.getMessage());
                                    onComplete.run();
                                });
                        } else {
                            onComplete.run();
                        }
                    },
                    failure -> {
                        log.debug("Could not retrieve messages for ticket {}: {}", ticket.getFormattedTicketId(), failure.getMessage());
                        onComplete.run();
                    });
            return;
        }

        channel.retrieveMessageById(ticket.getButtonControllerMessageId())
            .queue(message -> message.editMessageComponents(buildControlPanelComponents(ticket).toArray(ActionRow[]::new))
                    .queue(success -> onComplete.run(), failure -> {
                        log.debug("Skipping control panel update for {}: {}", ticket.getFormattedTicketId(), failure.getMessage());
                        onComplete.run();
                    }),
                failure -> {
                    log.debug("Skipping control panel update for {}: {}", ticket.getFormattedTicketId(), failure.getMessage());
                    onComplete.run();
                });
    }

    /**
     * Ensure the control message ID is set on the ticket.
     */
    public void ensureButtonControllerMessageId(Ticket ticket) {
        if (ticket == null || ticket.getButtonControllerMessageId() != null) {
            return;
        }

        TextChannel channel = DiscordBotEnvironment.getBot().getJDA().getTextChannelById(ticket.getChannelId());
        if (channel == null) {
            return;
        }

        try {
            List<Message> messages = channel.getHistory().retrievePast(1).complete();
            if (!messages.isEmpty()) {
                ticket.setButtonControllerMessageId(messages.getFirst().getId());
                ticketRepository.saveToDatabase(ticket);
            }
        } catch (Exception e) {
            log.debug("Unable to resolve control message for ticket {}: {}", ticket.getFormattedTicketId(), e.getMessage());
        }
    }

    /**
     * Check if a channel is a ticket channel (belongs to the ticket category).
     */
    public boolean isTicketChannel(TextChannel channel) {
        Optional<Category> ticketCategory = getTicketCategory();
        Optional<Category> closedCategory = getClosedTicketCategory();

        String parentId = channel.getParentCategoryId();
        if (parentId == null) {
            return false;
        }

        boolean inOpenCategory = ticketCategory.isPresent() && ticketCategory.get().getId().equals(parentId);
        boolean inClosedCategory = closedCategory.isPresent() && closedCategory.get().getId().equals(parentId);

        return inOpenCategory || inClosedCategory;
    }

    private String generateChannelName(Ticket ticket, User user) {
        String userName = sanitizeChannelName(user.getName());
        String name = config.getTicketChannelPrefix() + String.format("%04d", ticket.getTicketNumber()) + "-" + userName;

        // Limit to 100 characters (Discord limit)
        if (name.length() > 100) {
            name = name.substring(0, 100);
        }
        return name;
    }

    private String sanitizeChannelName(String name) {
        // Discord channel names can only contain alphanumeric, hyphens, and underscores
        return name.toLowerCase()
            .replaceAll("[^a-z0-9-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

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

    private List<ActionRow> buildControlPanelComponents(Ticket ticket) {
        List<ActionRow> rows = new ArrayList<>();

        // Add claim button if applicable (kept as a button for quick access)
        TicketAction claimAction = BuiltInTicketActions.CLAIM;
        if (claimAction.isVisible(ticket)) {
            Button claimButton = Button.of(claimAction.getStyle(), "ticket:action:" + claimAction.getId(), claimAction.getLabel());
            if (!claimAction.isEnabled(ticket)) {
                claimButton = claimButton.withDisabled(true);
            }
            rows.add(ActionRow.of(claimButton));
        }

        // Build status transition select menu
        List<TicketStatusTransition> transitions = config.getAllowedTransitions(ticket.getStatus());
        List<SelectOption> options = new ArrayList<>();

        for (TicketStatusTransition transition : transitions) {
            TicketStatus targetStatus = TicketStatus.fromId(transition.getTargetStatusId());
            if (targetStatus == ticket.getStatus()) {
                continue; // Skip if already in this status
            }

            String label = transition.getButtonLabel() != null && !transition.getButtonLabel().isEmpty()
                ? transition.getButtonLabel()
                : config.getStatusDisplayName(targetStatus);

            String description = getStatusTransitionDescription(targetStatus);

            SelectOption option = SelectOption.of(label, transition.getTargetStatusId())
                .withDescription(description);

            // Add emoji if configured
            Optional<String> emoji = config.getStatusEmoji(targetStatus);
            if (emoji.isPresent()) {
                option = option.withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode(emoji.get()));
            }

            options.add(option);
        }

        if (!options.isEmpty()) {
            // Show current status in placeholder
            String currentStatusName = config.getStatusDisplayName(ticket.getStatus());
            Optional<String> currentEmoji = config.getStatusEmoji(ticket.getStatus());
            String placeholder = currentEmoji.map(e -> e + " ").orElse("") + currentStatusName;

            StringSelectMenu statusMenu = StringSelectMenu.create("ticket:status-select")
                .setPlaceholder(placeholder)
                .addOptions(options)
                .build();
            rows.add(ActionRow.of(statusMenu));
        }

        return rows;
    }

    /**
     * Get a brief description for a status transition.
     */
    private String getStatusTransitionDescription(TicketStatus targetStatus) {
        return switch (targetStatus) {
            case OPEN -> "Mark as open and awaiting staff";
            case IN_PROGRESS -> "Staff is actively working on this";
            case AWAITING_RESPONSE -> "Waiting for user response";
            case CLOSED -> "Close this ticket";
        };
    }

    /**
     * Get the button style for a target status.
     */
    private ButtonStyle getButtonStyleForStatus(TicketStatus status) {
        return switch (status) {
            case CLOSED -> ButtonStyle.DANGER;
            case OPEN -> ButtonStyle.SUCCESS;
            case IN_PROGRESS -> ButtonStyle.PRIMARY;
            case AWAITING_RESPONSE -> ButtonStyle.SECONDARY;
        };
    }

    private void addTicketRolePermissions(TextChannel channel) {
        String ticketRoleId = config.getTicketRoleId();
        if (ticketRoleId == null || ticketRoleId.isEmpty()) {
            return;
        }

        RoleManager.getRoleById(ticketRoleId).ifPresent(role ->
            channel.upsertPermissionOverride(role)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_MANAGE)
                .queue(
                    success -> log.debug("Added ticket role permissions to channel {}", channel.getName()),
                    error -> log.debug("Failed to add ticket role permissions to channel {}", channel.getName())
                )
        );
    }

    private void pingTicketRole(TextChannel channel) {
        String ticketRoleId = config.getTicketRoleId();
        if (ticketRoleId != null && !ticketRoleId.isEmpty()) {
            channel.sendMessage("<@&" + ticketRoleId + ">").queue(
                null,
                error -> log.error("Failed to ping ticket role in channel {}", channel.getId(), error)
            );
        }
    }

    /**
     * Create a private thread for internal staff discussions.
     * Only staff members with the ticket role can see this thread.
     */
    private void createInternalThread(TextChannel channel, Ticket ticket) {
        try {
            String threadName = "Internal - " + ticket.getFormattedTicketId();
            ThreadChannel thread = channel.createThreadChannel(threadName, true).complete();

            ticket.setInternalThreadId(thread.getId());
            ticketRepository.saveToDatabase(ticket);

            // Add all staff members with the ticket role to the thread
            String ticketRoleId = config.getTicketRoleId();
            if (ticketRoleId != null && !ticketRoleId.isEmpty()) {
                RoleManager.getRoleById(ticketRoleId).ifPresent(role -> {
                    channel.getGuild().getMembersWithRoles(role).forEach(member ->
                        thread.addThreadMember(member).queue(
                            null,
                            error -> log.debug("Could not add {} to internal thread", member.getId())
                        )
                    );
                });
            }

            // Send initial message
            thread.sendMessage("**Internal Staff Discussion**\nThis thread is only visible to staff. Use it for internal notes about this ticket.").queue();

            log.debug("Created internal thread {} for ticket {}", thread.getId(), ticket.getFormattedTicketId());
        } catch (Exception e) {
            log.error("Failed to create internal thread for ticket {}", ticket.getFormattedTicketId(), e);
        }
    }

    private List<String> splitMessage(String content) {
        return IntStream.iterate(0, i -> i < content.length(), i -> i + MAX_MESSAGE_LENGTH)
            .mapToObj(i -> content.substring(i, Math.min(i + MAX_MESSAGE_LENGTH, content.length())))
            .toList();
    }
}