package net.hypixel.nerdbot.app.ticket;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplate;
import net.hypixel.nerdbot.discord.config.channel.TicketTemplateField;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketFieldValue;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Primary event listener for the ticketing system.
 * Handles button interactions, modal submissions, channel messages, and menu interactions.
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
        if (ticketService.getTicketCategory().isEmpty()) {
            return;
        }

        // Direct users to use the ticket panel in the server
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Tickets")
            .setDescription("To create or reply to a ticket, please use the **Create Ticket** button in the server.")
            .setColor(0x5865F2)
            .setFooter("If you have an open ticket, you can reply directly in your ticket channel.");

        event.getChannel().sendMessageEmbeds(embed.build()).queue(
            null,
            error -> log.debug("Failed to send DM redirect message", error)
        );
    }

    @SubscribeEvent
    public void onTicketChannelMessage(MessageReceivedEvent event) {
        User author = event.getAuthor();
        if (author.isBot() || author.isSystem()) {
            return;
        }

        if (event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        TicketService ticketService = TicketService.getInstance();

        if (!ticketService.isTicketChannel(channel)) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentDisplay();

        // Hidden message - add muted reaction and don't process further
        if (content.startsWith("?")) {
            message.addReaction(Emoji.fromUnicode("\uD83D\uDD07")).queue(
                null,
                error -> log.debug("Failed to add mute reaction to hidden message", error)
            );
            return;
        }

        boolean isStaff = isStaffMember(event.getMember());

        ticketService.handleTicketMessage(
            author,
            channel,
            content,
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

            // Always use modal flow for the new ephemeral-based ticket creation
            Optional<TicketTemplate> templateOpt = config.getTemplateForCategory(categoryId);
            Modal modal = templateOpt
                .map(ticketTemplate -> buildModalFromTemplate(ticketTemplate, user.getId()))
                .orElseGet(() -> buildDefaultModal(categoryId, user.getId(), config));

            event.replyModal(modal).queue(
                null,
                error -> log.error("Failed to show modal to user {}", user.getId(), error)
            );
        }

        // Handle status change select menu
        if (componentId.equals("ticket:status-select")) {
            event.deferReply(true).queue(
                null,
                error -> log.error("Failed to defer reply for status select", error)
            );
            String targetStatusId = event.getValues().getFirst();
            TicketButtonHandler.handleStatusSelect(event, targetStatusId);
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
            event.reply("Invalid modal data.").setEphemeral(true).queue(
                null,
                error -> log.error("Failed to send invalid modal data reply", error)
            );
            return;
        }

        String userId = parts[2];
        String categoryId = parts[3];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("You can only use your own ticket form!").setEphemeral(true).queue(
                null,
                error -> log.error("Failed to send unauthorized modal reply", error)
            );
            return;
        }

        event.deferReply(true).queue(
            null,
            error -> log.error("Failed to defer modal reply for user {}", userId, error)
        );

        // Build description and extract field values from modal
        StringBuilder description = new StringBuilder();
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        Optional<TicketTemplate> templateOpt = config.getTemplateForCategory(categoryId);

        // Store field values for structured storage
        List<TicketFieldValue> fieldDataList = new ArrayList<>();

        if (templateOpt.isPresent()) {
            TicketTemplate template = templateOpt.get();
            for (TicketTemplateField field : template.getFields()) {
                ModalMapping mapping = event.getValue(field.getId());
                if (mapping != null && !mapping.getAsString().isEmpty()) {
                    String value = mapping.getAsString();
                    description.append("**").append(field.getLabel()).append(":**\n");
                    description.append(value).append("\n\n");

                    // Store for structured field storage
                    fieldDataList.add(new TicketFieldValue(field.getId(), field.getLabel(), value));
                }
            }
        } else {
            // Fallback if template not found - just use description field
            for (ModalMapping mapping : event.getValues()) {
                description.append(mapping.getAsString()).append("\n\n");
            }
        }

        try {
            TicketService ticketService = TicketService.getInstance();
            Ticket ticket = ticketService.createTicket(event.getUser(), categoryId, description.toString().trim());

            // Store custom field values on the ticket
            for (TicketFieldValue fieldValue : fieldDataList) {
                ticket.addCustomField(fieldValue);
            }

            // Save updated ticket with custom fields
            if (!fieldDataList.isEmpty()) {
                ticketService.getTicketRepository().saveToDatabase(ticket);
                log.debug("Stored {} custom fields for ticket {}", fieldDataList.size(), ticket.getFormattedTicketId());
            }

            event.getHook().editOriginal(String.format("""
                                                       **Ticket Created Successfully!**

                                                       **Ticket ID:** %s
                                                       **Category:** %s

                                                       Your ticket has been created: <#%s>
                                                       Our team will respond shortly.
                                                       """, ticket.getFormattedTicketId(), config.getCategoryDisplayName(categoryId), ticket.getChannelId())).queue(
                null,
                error -> log.error("Failed to send ticket confirmation to user {}", userId, error)
            );
        } catch (IllegalStateException e) {
            event.getHook().editOriginal("Error: " + e.getMessage()).queue(
                null,
                error -> log.error("Failed to send error response to user {}", userId, error)
            );
        } catch (Exception e) {
            log.error("Failed to create ticket from modal for user {}", userId, e);
            event.getHook().editOriginal("An error occurred while creating your ticket.").queue(
                null,
                error -> log.error("Failed to send error response to user {}", userId, error)
            );
        }
    }

    @SubscribeEvent
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        User user = event.getUser();

        switch (componentId) {
            case "ticket:create" -> {
                // Show ephemeral category selection
                TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

                // Check if user is blacklisted
                if (config.isUserBlacklisted(user.getId())) {
                    event.reply(config.getBlacklistMessage())
                        .setEphemeral(true)
                        .queue(null, error -> log.error("Failed to send blacklist message", error));
                    return;
                }

                // Check max open tickets
                int openTickets = TicketService.getInstance().getTicketRepository()
                    .findOpenTicketsByUser(user.getId()).size();
                if (openTickets >= config.getMaxOpenTicketsPerUser()) {
                    event.reply("You already have " + openTickets + " open ticket(s). Please wait for your existing tickets to be resolved before creating a new one.")
                        .setEphemeral(true)
                        .queue(null, error -> log.error("Failed to send max tickets message", error));
                    return;
                }

                List<SelectOption> categoryOptions = config.getCategories().stream()
                    .map(c -> SelectOption.of(c.getDisplayName(), c.getId())
                        .withDescription(c.getDescription()))
                    .toList();

                StringSelectMenu categoryMenu = StringSelectMenu.create("ticket:select-category")
                    .setPlaceholder("Select a category...")
                    .addOptions(categoryOptions)
                    .build();

                event.reply("**Select a category for your ticket:**")
                    .setEphemeral(true)
                    .addActionRow(categoryMenu)
                    .queue(null, error -> log.error("Failed to show category selection", error));
            }
            case "ticket:new" -> {
                // Legacy DM flow - redirect to ephemeral flow
                TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();

                List<SelectOption> categoryOptions = config.getCategories().stream()
                    .map(c -> SelectOption.of(c.getDisplayName(), c.getId())
                        .withDescription(c.getDescription()))
                    .toList();

                StringSelectMenu categoryMenu = StringSelectMenu.create("ticket:select-category")
                    .setPlaceholder("Select a category...")
                    .addOptions(categoryOptions)
                    .build();

                event.reply("**Select a category for your ticket:**")
                    .setEphemeral(true)
                    .addActionRow(categoryMenu)
                    .queue(null, error -> log.error("Failed to show category selection", error));
            }
            case "ticket:menu", "ticket:cancel" -> {
                // These are no longer needed with the new flow
                event.reply("This button is no longer supported. Use the Create Ticket button instead.")
                    .setEphemeral(true)
                    .queue(null, error -> log.error("Failed to send deprecated button message", error));
            }
            case "ticket:channel:close" -> handleChannelControlButton(event, ChannelButtonAction.CLOSE);
            case "ticket:channel:reopen" -> handleChannelControlButton(event, ChannelButtonAction.REOPEN);
            case "ticket:channel:claim" -> handleChannelControlButton(event, ChannelButtonAction.CLAIM);
            default -> {
                // Handle action-based buttons (ticket:action:*) and status transition buttons (ticket:status:*)
                if (componentId.startsWith("ticket:action:") || componentId.startsWith("ticket:status:")) {
                    event.deferReply(true).queue(
                        null,
                        error -> log.error("Failed to defer reply for ticket button", error)
                    );
                    TicketButtonHandler.handleButtonClick(event);
                }
            }
        }
    }

    private Modal buildModalFromTemplate(TicketTemplate template, String userId) {
        String modalId = "ticket-create-" + userId + "-" + template.getCategoryId();

        List<ActionRow> actionRows = template.getFields().stream()
            .map(field -> {
                TextInputStyle style = field.getStyle() != null && field.getStyle().equalsIgnoreCase("SHORT")
                    ? TextInputStyle.SHORT
                    : TextInputStyle.PARAGRAPH;

                TextInput.Builder inputBuilder = TextInput.create(field.getId(), field.getLabel(), style)
                    .setRequired(field.isRequired())
                    .setRequiredRange(field.getMinLength(), field.getMaxLength());

                if (field.getPlaceholder() != null && !field.getPlaceholder().isEmpty()) {
                    inputBuilder.setPlaceholder(field.getPlaceholder());
                }

                return ActionRow.of(inputBuilder.build());
            })
            .toList();

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

    private boolean isStaffMember(Member member) {
        if (member == null) {
            return false;
        }

        TicketConfig ticketConfig = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        String ticketRoleId = ticketConfig.getTicketRoleId();

        if (ticketRoleId == null || ticketRoleId.isEmpty()) {
            return false;
        }

        Role targetRole = member.getGuild().getRoleById(ticketRoleId);
        if (targetRole == null) {
            return false;
        }

        return member.getRoles().stream()
            .anyMatch(role -> role.getPositionRaw() >= targetRole.getPositionRaw());
    }

    @SubscribeEvent
    public void onChannelDeleted(ChannelDeleteEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel)) {
            return;
        }

        TicketService service = TicketService.getInstance();
        if (!service.isTicketChannel(channel)) {
            return;
        }

        service.getTicketRepository().findByChannelId(channel.getId())
            .ifPresent(ticket -> {
                service.getTicketRepository().deleteFromDatabase(String.valueOf(ticket.getTicketNumber()));
                log.info("Deleted ticket {} because channel {} no longer exists", ticket.getFormattedTicketId(), channel.getId());
            });
    }

    private void handleChannelControlButton(ButtonInteractionEvent event, ChannelButtonAction action) {
        event.deferReply(true).queue(
            null,
            error -> log.error("Failed to defer reply for channel control button {}", action, error)
        );

        if (!(event.getChannel() instanceof TextChannel channel)) {
            event.getHook().editOriginal("This button must be used inside a ticket channel.").queue(
                null,
                error -> log.debug("Failed to send not-in-channel response", error)
            );
            return;
        }

        TicketService service = TicketService.getInstance();
        Optional<Ticket> ticketOpt = service.getTicketRepository().findByChannelId(channel.getId());
        if (ticketOpt.isEmpty()) {
            event.getHook().editOriginal("This is not a ticket channel.").queue(
                null,
                error -> log.debug("Failed to send not-ticket response", error)
            );
            return;
        }

        if (!isStaffMember(event.getMember())) {
            event.getHook().editOriginal("You don't have permission to manage this ticket.").queue(
                null,
                error -> log.debug("Failed to send no-permission response", error)
            );
            return;
        }

        Ticket ticket = ticketOpt.get();

        try {
            String response = switch (action) {
                case CLOSE -> {
                    if (ticket.isClosed()) {
                        yield "This ticket is already closed.";
                    }
                    service.closeTicket(ticket, event.getUser(), "Closed by staff");
                    yield "Closed ticket " + ticket.getFormattedTicketId() + ".";
                }
                case REOPEN -> {
                    if (!ticket.isClosed()) {
                        yield "This ticket is not closed.";
                    }
                    service.reopenTicket(ticket, event.getUser(), "Re-opened by staff");
                    yield "Reopened ticket " + ticket.getFormattedTicketId() + ".";
                }
                case CLAIM -> {
                    if (ticket.isClosed()) {
                        yield "Cannot claim a closed ticket.";
                    }
                    if (ticket.isClaimed()) {
                        yield "This ticket is already claimed by <@" + ticket.getClaimedById() + ">.";
                    }
                    service.claimTicket(ticket, event.getUser());
                    yield "You have claimed ticket " + ticket.getFormattedTicketId() + ".";
                }
            };

            event.getHook().editOriginal(response).queue(
                null,
                error -> log.error("Failed to send {} response for ticket {}", action, ticket.getFormattedTicketId(), error)
            );
        } catch (Exception e) {
            log.error("Failed to handle channel control button {} for ticket {}", action, ticket.getChannelId(), e);
            event.getHook().editOriginal("Something went wrong: " + e.getMessage()).queue(
                null,
                error -> log.error("Failed to send error response", error)
            );
        }
    }

    private enum ChannelButtonAction {
        CLOSE,
        REOPEN,
        CLAIM
    }
}