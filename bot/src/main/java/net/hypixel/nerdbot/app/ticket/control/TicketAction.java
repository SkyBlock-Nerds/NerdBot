package net.hypixel.nerdbot.app.ticket.control;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;

public interface TicketAction {
    String getId();

    String getLabel();

    ButtonStyle getStyle();

    boolean isVisible(Ticket ticket);

    boolean isEnabled(Ticket ticket);

    void handle(Ticket ticket, User actor, ButtonInteractionEvent event);
}