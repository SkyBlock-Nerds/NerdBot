package net.hypixel.nerdbot.app.ticket.control;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;

/**
 * Built-in ticket actions for control panel buttons.
 * Each action handles visibility, enabled state, and execution logic.
 */
@Slf4j
@UtilityClass
public class BuiltInTicketActions {

    private static final String ERROR_MESSAGE = "An error occurred while processing your request.";
    public static final TicketAction CLAIM = new TicketAction() {
        @Override
        public String getId() {
            return "claim";
        }

        @Override
        public String getLabel() {
            return "Claim Ticket";
        }

        @Override
        public ButtonStyle getStyle() {
            return ButtonStyle.PRIMARY;
        }

        @Override
        public boolean isVisible(Ticket ticket) {
            return !ticket.isClosed();
        }

        @Override
        public boolean isEnabled(Ticket ticket) {
            return !ticket.isClosed() && !ticket.isClaimed();
        }

        @Override
        public void handle(Ticket ticket, User actor, ButtonInteractionEvent event) {
            try {
                TicketService service = TicketService.getInstance();
                service.claimTicket(ticket, actor);
                event.getHook().editOriginal("You have claimed ticket " + ticket.getFormattedTicketId() + ".").queue();
            } catch (Exception e) {
                log.error("Failed to claim ticket {}", ticket.getFormattedTicketId(), e);
                event.getHook().editOriginal(ERROR_MESSAGE).queue();
            }
        }
    };

    public static final TicketAction CLOSE = new TicketAction() {
        @Override
        public String getId() {
            return "close";
        }

        @Override
        public String getLabel() {
            return "Close Ticket";
        }

        @Override
        public ButtonStyle getStyle() {
            return ButtonStyle.DANGER;
        }

        @Override
        public boolean isVisible(Ticket ticket) {
            return !ticket.isClosed();
        }

        @Override
        public boolean isEnabled(Ticket ticket) {
            return !ticket.isClosed();
        }

        @Override
        public void handle(Ticket ticket, User actor, ButtonInteractionEvent event) {
            try {
                TicketService service = TicketService.getInstance();
                service.closeTicket(ticket, actor, "Closed via button");
                event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " closed.").queue();
            } catch (Exception e) {
                log.error("Failed to close ticket {}", ticket.getFormattedTicketId(), e);
                event.getHook().editOriginal(ERROR_MESSAGE).queue();
            }
        }
    };

    public static final TicketAction REOPEN = new TicketAction() {
        @Override
        public String getId() {
            return "reopen";
        }

        @Override
        public String getLabel() {
            return "Reopen Ticket";
        }

        @Override
        public ButtonStyle getStyle() {
            return ButtonStyle.SUCCESS;
        }

        @Override
        public boolean isVisible(Ticket ticket) {
            return ticket.isClosed();
        }

        @Override
        public boolean isEnabled(Ticket ticket) {
            return ticket.isClosed();
        }

        @Override
        public void handle(Ticket ticket, User actor, ButtonInteractionEvent event) {
            try {
                TicketService service = TicketService.getInstance();
                service.reopenTicket(ticket, actor, "Reopened via button");
                event.getHook().editOriginal("Ticket " + ticket.getFormattedTicketId() + " reopened.").queue();
            } catch (Exception e) {
                log.error("Failed to reopen ticket {}", ticket.getFormattedTicketId(), e);
                event.getHook().editOriginal(ERROR_MESSAGE).queue();
            }
        }
    };

}