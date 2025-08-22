package net.hypixel.nerdbot.listener;

import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.hypixel.nerdbot.util.pagination.PaginationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaginationListener {

    private static final Logger log = LoggerFactory.getLogger(PaginationListener.class);

    @SlashComponentHandler(id = "pagination", patterns = {"suggestions-page:*", "profile-page:*", "info-page:*"})
    public void handlePaginationButtons(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        boolean handled = PaginationManager.handleButtonInteraction(event);

        if (!handled) {
            log.warn("Could not find pagination for message ID: {}", event.getMessageId());
            event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
        }
    }
}