package net.hypixel.nerdbot.app.ticket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the process that guides users through creating
 * and replying to tickets via Discord direct messages.
 */
@Slf4j
public class TicketConversationManager {

    private static final Cache<@NotNull String, ConversationState> CONVERSATIONS = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .build();

    private TicketConversationManager() {
    }

    /**
     * Handle a DM from a user
     */
    public static void handleDM(User user, String content, List<Message.Attachment> attachments) {
        ConversationState state = CONVERSATIONS.getIfPresent(user.getId());

        // If user is in REPLYING_TO_TICKET state, verify the ticket is still valid
        if (state != null && state.getStep() == ConversationStep.REPLYING_TO_TICKET) {
            boolean ticketStillValid = state.getReplyToThreadId() != null &&
                TicketService.getInstance()
                    .getTicketRepository()
                    .findByThreadId(state.getReplyToThreadId())
                    .filter(t -> !t.isClosed())
                    .isPresent();

            if (!ticketStillValid) {
                // Ticket was closed or no longer exists, clear state and re-evaluate
                CONVERSATIONS.invalidate(user.getId());
                state = null;
            }
        }

        if (state == null) {
            List<Ticket> openTickets = TicketService.getInstance().getTicketRepository()
                .findOpenTicketsByUser(user.getId());

            if (!openTickets.isEmpty()) {
                showTicketOptions(user, openTickets);
            } else {
                startNewTicketFlow(user);
            }
            return;
        }

        switch (state.getStep()) {
            case ENTERING_DESCRIPTION -> handleDescriptionEntry(user, content, state);
            case REPLYING_TO_TICKET -> handleTicketReply(user, content, attachments, state);
            default -> sendHelpMessage(user);
        }
    }

    /**
     * Show the ticket options menu (called from button interaction)
     */
    public static void showTicketMenu(User user) {
        CONVERSATIONS.invalidate(user.getId());

        List<Ticket> openTickets = TicketService.getInstance().getTicketRepository()
            .findOpenTicketsByUser(user.getId());

        if (!openTickets.isEmpty()) {
            showTicketOptions(user, openTickets);
        } else {
            startNewTicketFlow(user);
        }
    }

    /**
     * Show options when user has existing tickets
     */
    private static void showTicketOptions(User user, List<Ticket> openTickets) {
        ConversationState state = new ConversationState(user.getId(), ConversationStep.INITIAL, null, null);
        CONVERSATIONS.put(user.getId(), state);

        List<SelectOption> ticketOptions = openTickets.stream()
            .map(t -> {
                String lastMessage = getLastMessagePreview(t);
                return SelectOption.of(
                        t.getFormattedTicketId() + " - " + t.getCategoryId(),
                        t.getThreadId())
                    .withDescription(lastMessage);
            })
            .limit(25)
            .toList();

        StringSelectMenu ticketMenu = StringSelectMenu.create("ticket:select-reply")
            .setPlaceholder("Reply to existing ticket...")
            .addOptions(ticketOptions)
            .build();

        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage(String.format("""
                                                        You have **%d** open ticket(s). What would you like to do?
                                                        
                                                        **Reply to an existing ticket** - Select from the menu below
                                                        **Create a new ticket** - Click the button below
                                                        """, openTickets.size()))
                .setComponents(
                    ActionRow.of(ticketMenu),
                    ActionRow.of(
                        Button.primary("ticket:new", "Create New Ticket").withEmoji(Emoji.fromUnicode("➕")),
                        Button.secondary("ticket:cancel", "Cancel")
                    )
                ))
            .queue();
    }

    /**
     * Start flow for creating a new ticket
     */
    public static void startNewTicketFlow(User user) {
        ConversationState state = new ConversationState(user.getId(), ConversationStep.SELECTING_CATEGORY, null, null);
        CONVERSATIONS.put(user.getId(), state);

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

        List<SelectOption> categoryOptions = config.getCategories().stream()
            .map(c -> SelectOption.of(c.getDisplayName(), c.getId())
                .withDescription(c.getDescription()))
            .toList();

        StringSelectMenu categoryMenu = StringSelectMenu.create("ticket:select-category")
            .setPlaceholder("Select a category...")
            .addOptions(categoryOptions)
            .build();

        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage("""
                                          **Create a New Ticket**
                                          
                                          Please select a category for your ticket:
                                          """)
                .setComponents(ActionRow.of(categoryMenu)))
            .queue();
    }

    /**
     * Handle category selection
     */
    public static void handleCategorySelection(User user, String categoryId) {
        ConversationState state = CONVERSATIONS.getIfPresent(user.getId());
        if (state == null) {
            state = new ConversationState(user.getId(), ConversationStep.SELECTING_CATEGORY, null, null);
        }

        state.setSelectedCategory(categoryId);
        state.setStep(ConversationStep.ENTERING_DESCRIPTION);
        CONVERSATIONS.put(user.getId(), state);

        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        String categoryName = config.getCategories().stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .map(TicketConfig.TicketCategory::getDisplayName)
            .orElse(categoryId);

        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage(String.format("""
                                                        **Category selected:** %s
                                                        
                                                        Please describe your issue or question in detail.
                                                        Type your message below:
                                                        """, categoryName)))
            .queue();
    }

    /**
     * Handle ticket selection for reply
     */
    public static void handleTicketSelection(User user, String threadId) {
        ConversationState state = CONVERSATIONS.getIfPresent(user.getId());
        if (state == null) {
            state = new ConversationState(user.getId(), ConversationStep.REPLYING_TO_TICKET, null, null);
        }

        state.setReplyToThreadId(threadId);
        state.setStep(ConversationStep.REPLYING_TO_TICKET);
        CONVERSATIONS.put(user.getId(), state);

        TicketService service = TicketService.getInstance();
        String ticketId = service.getTicketRepository().findByThreadId(threadId)
            .map(Ticket::getFormattedTicketId)
            .orElse("Unknown");

        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage(String.format("""
                                                        **Replying to ticket %s**
                                                        
                                                        Type your message below. It will be sent to the support team.
                                                        """, ticketId)))
            .queue();
    }

    /**
     * Handle description entry and create ticket
     */
    private static void handleDescriptionEntry(User user, String content, ConversationState state) {
        try {
            Ticket ticket = TicketService.getInstance()
                .createTicket(user, state.getSelectedCategory(), content);

            user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessage(String.format("""
                                                            **Ticket Created Successfully!**
                                                            
                                                            **Ticket ID:** %s
                                                            **Category:** %s
                                                            
                                                            Our team will respond to your ticket shortly.
                                                            You can reply to this DM to add more information to your ticket.
                                                            """, ticket.getFormattedTicketId(), state.getSelectedCategory()))
                    .setComponents(ActionRow.of(
                        Button.secondary("ticket:menu", "View Tickets").withEmoji(Emoji.fromUnicode("\uD83D\uDCCB")),
                        Button.primary("ticket:new", "Create Another").withEmoji(Emoji.fromUnicode("➕"))
                    )))
                .queue();

            state.setReplyToThreadId(ticket.getThreadId());
            state.setStep(ConversationStep.REPLYING_TO_TICKET);
            CONVERSATIONS.put(user.getId(), state);

        } catch (IllegalStateException e) {
            user.openPrivateChannel()
                .flatMap(ch -> ch.sendMessage("**Error:** " + e.getMessage()))
                .queue();
            CONVERSATIONS.invalidate(user.getId());
        }
    }

    /**
     * Handle reply to existing ticket
     */
    private static void handleTicketReply(User user, String content, List<Message.Attachment> attachments, ConversationState state) {
        TicketService.getInstance().handleUserReply(user, state.getReplyToThreadId(), content, attachments);

        TicketService service = TicketService.getInstance();
        String ticketId = service.getTicketRepository().findByThreadId(state.getReplyToThreadId())
            .map(Ticket::getFormattedTicketId)
            .orElse("your ticket");

        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage(String.format("Your message has been sent to **%s**.", ticketId))
                .setComponents(ActionRow.of(
                    Button.secondary("ticket:menu", "Switch Ticket").withEmoji(Emoji.fromUnicode("\uD83D\uDD00")),
                    Button.primary("ticket:new", "Create New Ticket").withEmoji(Emoji.fromUnicode("➕"))
                )))
            .queue();
    }

    /**
     * Clear conversation state
     */
    public static void clearConversation(String userId) {
        CONVERSATIONS.invalidate(userId);
    }

    /**
     * Get current conversation state
     */
    public static ConversationState getState(String userId) {
        return CONVERSATIONS.getIfPresent(userId);
    }

    /**
     * Set conversation state (used for modal-based ticket creation)
     */
    public static void setState(String userId, ConversationState state) {
        CONVERSATIONS.put(userId, state);
    }

    private static void sendHelpMessage(User user) {
        user.openPrivateChannel()
            .flatMap(ch -> ch.sendMessage("""
                                          I didn't understand that. Here's what you can do:
                                          
                                          - **Create a new ticket** - Just type your message and I'll guide you through the process
                                          - **Reply to an existing ticket** - If you have open tickets, I'll show you options
                                          
                                          Send a message to get started!
                                          """))
            .queue();
        CONVERSATIONS.invalidate(user.getId());
    }

    /**
     * Get a preview of the last message in a ticket for display in selection menu
     */
    private static String getLastMessagePreview(Ticket ticket) {
        if (ticket.getMessages() == null || ticket.getMessages().isEmpty()) {
            return "No messages yet";
        }

        var lastMessage = ticket.getMessages().getLast();
        String content = lastMessage.getContent();
        String prefix = lastMessage.isStaff() ? "Staff: " : "You: ";

        // Discord description limit is 100 characters
        int maxLength = 100 - prefix.length();
        if (content.length() > maxLength) {
            content = content.substring(0, maxLength - 3) + "...";
        }

        return prefix + content;
    }

    public enum ConversationStep {
        INITIAL,
        SELECTING_CATEGORY,
        ENTERING_DESCRIPTION,
        REPLYING_TO_TICKET
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class ConversationState {
        private String userId;
        private ConversationStep step;
        private String selectedCategory;
        private String replyToThreadId;
    }
}