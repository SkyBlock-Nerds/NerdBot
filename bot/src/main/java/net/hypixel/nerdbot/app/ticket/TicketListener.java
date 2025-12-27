package net.hypixel.nerdbot.app.ticket;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplate;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplateField;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary event listener for the ticketing system.
 * Handles DM conversations, modal submissions, thread messages, and menu interactions.
 */
@Slf4j
public class TicketListener {

    @SubscribeEvent
    public void onPrivateMessage(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        TicketService ticketService = TicketService.getInstance();
        if (ticketService.getTicketChannel().isEmpty()) {
            return;
        }

        Message message = event.getMessage();
        TicketConversationManager.handleDM(author, message.getContentDisplay(), message.getAttachments());
    }

    @SubscribeEvent
    public void onTicketThreadMessage(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return;
        }

        ThreadChannel thread = event.getChannel().asThreadChannel();
        TicketService ticketService = TicketService.getInstance();

        if (!ticketService.isTicketThread(thread)) {
            return;
        }

        Message message = event.getMessage();
        boolean isStaff = isStaffMember(event.getMember());

        ticketService.handleTicketMessage(
            author,
            thread,
            message.getContentDisplay(),
            message.getAttachments(),
            isStaff
        );
    }

    @SubscribeEvent
    public void onSelectMenuInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        User user = event.getUser();

        if (componentId.equals("ticket:select-category")) {
            String categoryId = event.getValues().getFirst();
            TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

            // Check if modal flow is enabled
            if (config.isUseModalFlow()) {
                Optional<TicketTemplate> templateOpt = config.getTemplateForCategory(categoryId);
                Modal modal;
                // Use default modal when no template is configured
                modal = templateOpt
                    .map(ticketTemplate -> buildModalFromTemplate(ticketTemplate, user.getId()))
                    .orElseGet(() -> buildDefaultModal(categoryId, user.getId(), config));
                event.replyModal(modal).queue();
                return;
            }

            // Fall back to DM-based flow
            event.deferEdit().queue();
            TicketConversationManager.handleCategorySelection(user, categoryId);
            event.getMessage().delete().queue();
        }

        if (componentId.equals("ticket:select-reply")) {
            event.deferEdit().queue();
            String threadId = event.getValues().getFirst();
            TicketConversationManager.handleTicketSelection(user, threadId);
            event.getMessage().delete().queue();
        }
    }

    @SubscribeEvent
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        // Only handle ticket creation modals
        if (!modalId.startsWith("ticket-create-")) {
            return;
        }

        // Modal ID format: ticket-create-{userId}-{categoryId}
        String[] parts = modalId.split("-");
        if (parts.length < 4) {
            event.reply("Invalid modal data.").setEphemeral(true).queue();
            return;
        }

        String userId = parts[2];
        String categoryId = parts[3];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own ticket form!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        // Build description from modal fields
        StringBuilder description = new StringBuilder();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<TicketTemplate> templateOpt = config.getTemplateForCategory(categoryId);

        if (templateOpt.isPresent()) {
            TicketTemplate template = templateOpt.get();
            for (TicketTemplateField field : template.getFields()) {
                ModalMapping mapping = event.getValue(field.getId());
                if (mapping != null && !mapping.getAsString().isEmpty()) {
                    description.append("**").append(field.getLabel()).append(":**\n");
                    description.append(mapping.getAsString()).append("\n\n");
                }
            }
        } else {
            // Fallback if template not found
            for (ModalMapping mapping : event.getValues()) {
                description.append(mapping.getAsString()).append("\n\n");
            }
        }

        try {
            Ticket ticket = TicketService.getInstance()
                .createTicket(event.getUser(), categoryId, description.toString().trim());

            event.getHook().editOriginal(String.format("""
                                                       **Ticket Created Successfully!**
                                                       
                                                       **Ticket ID:** %s
                                                       **Category:** %s
                                                       
                                                       Our team will respond to your ticket shortly.
                                                       You can reply via DM to add more information.
                                                       """, ticket.getFormattedTicketId(), categoryId)).queue();

            // Set up for DM replies
            TicketConversationManager.ConversationState state = new TicketConversationManager.ConversationState(
                userId,
                TicketConversationManager.ConversationStep.REPLYING_TO_TICKET,
                categoryId,
                ticket.getThreadId()
            );

            TicketConversationManager.setState(userId, state);
        } catch (IllegalStateException e) {
            event.getHook().editOriginal("**Error:** " + e.getMessage()).queue();
        } catch (Exception e) {
            log.error("Failed to create ticket from modal for user {}", userId, e);
            event.getHook().editOriginal("An error occurred while creating your ticket.").queue();
        }
    }

    private Modal buildModalFromTemplate(TicketTemplate template, String userId) {
        String modalId = "ticket-create-" + userId + "-" + template.getCategoryId();

        List<ActionRow> actionRows = new ArrayList<>();
        for (TicketTemplateField field : template.getFields()) {
            TextInputStyle style = field.getStyle() != null && field.getStyle().equalsIgnoreCase("SHORT")
                ? TextInputStyle.SHORT
                : TextInputStyle.PARAGRAPH;

            TextInput.Builder inputBuilder = TextInput.create(field.getId(), field.getLabel(), style)
                .setRequired(field.isRequired())
                .setRequiredRange(field.getMinLength(), field.getMaxLength());

            if (field.getPlaceholder() != null && !field.getPlaceholder().isEmpty()) {
                inputBuilder.setPlaceholder(field.getPlaceholder());
            }

            actionRows.add(ActionRow.of(inputBuilder.build()));
        }

        return Modal.create(modalId, template.getModalTitle())
            .addComponents(actionRows)
            .build();
    }

    private Modal buildDefaultModal(String categoryId, String userId, TicketConfig config) {
        String modalId = "ticket-create-" + userId + "-" + categoryId;

        String categoryName = config.getCategoryById(categoryId)
            .map(TicketConfig.TicketCategory::getDisplayName)
            .orElse(categoryId);

        TextInput descriptionInput = TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Please describe your issue or question in detail...")
            .setRequired(true)
            .setRequiredRange(10, 4_000)
            .build();

        return Modal.create(modalId, "New " + categoryName + " Ticket")
            .addComponents(ActionRow.of(descriptionInput))
            .build();
    }

    @SubscribeEvent
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        User user = event.getUser();

        switch (componentId) {
            case "ticket:new" -> {
                event.deferEdit().queue();
                TicketConversationManager.startNewTicketFlow(user);
                event.getMessage().delete().queue();
            }
            case "ticket:menu" -> {
                event.deferEdit().queue();
                TicketConversationManager.showTicketMenu(user);
                event.getMessage().delete().queue();
            }
            case "ticket:cancel" -> {
                event.deferEdit().queue();
                TicketConversationManager.clearConversation(user.getId());
                event.getMessage().editMessage("Cancelled.").setComponents().queue();
            }
        }
    }

    private boolean isStaffMember(Member member) {
        if (member == null) {
            return false;
        }
        String ticketRoleId = DiscordBotEnvironment.getBot()
            .getConfig().getTicketConfig().getTicketRoleId();
        if (ticketRoleId == null || ticketRoleId.isEmpty()) {
            return false;
        }
        return member.getRoles().stream()
            .anyMatch(r -> r.getId().equals(ticketRoleId));
    }
}