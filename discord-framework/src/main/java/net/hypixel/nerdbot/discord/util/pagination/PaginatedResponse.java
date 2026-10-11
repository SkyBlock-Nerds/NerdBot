package net.hypixel.nerdbot.discord.util.pagination;

import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Button clicks can arrive on different threads, so everything that reads or moves the current
 * page is synchronized.
 */
public class PaginatedResponse<T> {

    @Getter
    private final List<T> items;
    @Getter
    private final int itemsPerPage;
    private final Function<List<T>, MessageEditData> responseBuilder;
    private final String componentPrefix;
    private int currentPage;

    public PaginatedResponse(
        @NotNull List<T> items,
        int itemsPerPage,
        @NotNull Function<List<T>, MessageEditData> responseBuilder,
        @NotNull String componentPrefix
    ) {
        if (itemsPerPage < 1) {
            throw new IllegalArgumentException("itemsPerPage must be at least 1, got " + itemsPerPage);
        }

        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.itemsPerPage = itemsPerPage;
        this.responseBuilder = responseBuilder;
        this.componentPrefix = componentPrefix;
        this.currentPage = 0;
    }

    public static <T> PaginatedResponse<T> forEmbeds(@NotNull List<T> items, int itemsPerPage,
                                                     @NotNull Function<List<T>, MessageEmbed> embedBuilder,
                                                     @NotNull String componentPrefix) {
        return new PaginatedResponse<>(items, itemsPerPage, pageItems -> MessageEditData.fromEmbeds(embedBuilder.apply(pageItems)), componentPrefix);
    }

    public static <T> PaginatedResponse<T> forText(@NotNull List<T> items, int itemsPerPage,
                                                   @NotNull Function<List<T>, String> textBuilder,
                                                   @NotNull String componentPrefix) {
        return new PaginatedResponse<>(items, itemsPerPage, pageItems -> MessageEditData.fromContent(textBuilder.apply(pageItems)), componentPrefix);
    }

    public synchronized int getCurrentPage() {
        return currentPage;
    }

    public synchronized void sendMessage(SlashCommandInteractionEvent event) {
        MessageEditData response = buildCurrentPageResponse();
        List<ActionRow> components = currentComponents();

        if (event.isAcknowledged()) {
            event.getHook().editOriginal(response).setComponents(components).queue();
        } else {
            event.reply(MessageCreateData.fromEditData(response)).setComponents(components).queue();
        }
    }

    public synchronized void editMessage(ButtonInteractionEvent event) {
        event.getHook().editOriginal(buildCurrentPageResponse()).setComponents(currentComponents()).queue();
    }

    public synchronized boolean handleButtonInteraction(ButtonInteractionEvent event) {
        return handleAction(event.getComponentId());
    }

    /**
     * Moves the page for a button id of the form {@code <prefix>:<action>}. Returns false for ids
     * that belong to something else.
     */
    synchronized boolean handleAction(String componentId) {
        String expectedStart = componentPrefix + ":";
        if (!componentId.startsWith(expectedStart)) {
            return false;
        }

        int lastPage = getTotalPages() - 1;
        switch (componentId.substring(expectedStart.length())) {
            case "first" -> currentPage = 0;
            case "prev" -> currentPage = Math.max(0, currentPage - 1);
            case "next" -> currentPage = Math.min(lastPage, currentPage + 1);
            case "last" -> currentPage = lastPage;
            default -> {
                return false;
            }
        }
        return true;
    }

    synchronized MessageEditData buildCurrentPageResponse() {
        return responseBuilder.apply(getCurrentPageItems());
    }

    synchronized List<T> getCurrentPageItems() {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        return items.subList(startIndex, endIndex);
    }

    /** No buttons when everything fits on one page. */
    synchronized List<ActionRow> currentComponents() {
        return getTotalPages() > 1 ? List.of(buildActionRow()) : List.of();
    }

    private ActionRow buildActionRow() {
        boolean isFirstPage = currentPage == 0;
        boolean isLastPage = currentPage == getTotalPages() - 1;
        String pageIndicator = String.format("Page %d/%d", currentPage + 1, getTotalPages());

        return ActionRow.of(
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":first", "⏮️").withDisabled(isFirstPage),
            Button.of(ButtonStyle.PRIMARY, componentPrefix + ":prev", "◀️").withDisabled(isFirstPage),
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":page", pageIndicator).withDisabled(true),
            Button.of(ButtonStyle.PRIMARY, componentPrefix + ":next", "▶️").withDisabled(isLastPage),
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":last", "⏭️").withDisabled(isLastPage)
        );
    }

    /** At least one page, so an empty list still renders (as an empty page) instead of paging to -1. */
    int getTotalPages() {
        return Math.max(1, (items.size() + itemsPerPage - 1) / itemsPerPage);
    }
}
