package net.hypixel.nerdbot.util.pagination;

import lombok.Getter;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

public class PaginatedResponse<T> {

    @Getter
    private final List<T> items;
    @Getter
    private final int itemsPerPage;
    private final Function<List<T>, MessageEditData> responseBuilder;
    private final String componentPrefix;
    @Getter
    private int currentPage;

    public PaginatedResponse(
        @NotNull List<T> items, int itemsPerPage,
        @NotNull Function<List<T>, MessageEditData> responseBuilder,
        @NotNull String componentPrefix
    ) {
        this.items = items;
        this.itemsPerPage = itemsPerPage;
        this.responseBuilder = responseBuilder;
        this.componentPrefix = componentPrefix;
        this.currentPage = 0;
    }

    public static <T> PaginatedResponse<T> forEmbeds(@NotNull List<T> items, int itemsPerPage,
                                                     @NotNull Function<List<T>, MessageEmbed> embedBuilder,
                                                     @NotNull String componentPrefix) {
        Function<List<T>, MessageEditData> responseBuilder = pageItems -> {
            MessageEmbed embed = embedBuilder.apply(pageItems);
            return MessageEditData.fromEmbeds(embed);
        };
        return new PaginatedResponse<>(items, itemsPerPage, responseBuilder, componentPrefix);
    }

    public static <T> PaginatedResponse<T> forText(@NotNull List<T> items, int itemsPerPage,
                                                   @NotNull Function<List<T>, String> textBuilder,
                                                   @NotNull String componentPrefix) {
        Function<List<T>, MessageEditData> responseBuilder = pageItems -> {
            String text = textBuilder.apply(pageItems);
            return MessageEditData.fromContent(text);
        };
        return new PaginatedResponse<>(items, itemsPerPage, responseBuilder, componentPrefix);
    }

    public void sendMessage(SlashCommandInteractionEvent event) {
        if (event.isAcknowledged()) {
            // Interaction is deferred, use hook to edit
            MessageEditData response = buildCurrentPageResponse();
            if (getTotalPages() <= 1) {
                event.getHook().editOriginal(response).queue();
            } else {
                event.getHook().editOriginal(response).setComponents(buildActionRow()).queue();
            }
        } else {
            // Interaction not acknowledged, reply directly
            List<T> pageItems = getCurrentPageItems();
            MessageEditData editData = responseBuilder.apply(pageItems);
            
            if (getTotalPages() <= 1) {
                if (editData.getEmbeds().isEmpty()) {
                    event.reply(editData.getContent()).queue();
                } else {
                    event.replyEmbeds(editData.getEmbeds()).queue();
                }
            } else {
                if (editData.getEmbeds().isEmpty()) {
                    event.reply(editData.getContent()).setComponents(buildActionRow()).queue();
                } else {
                    event.replyEmbeds(editData.getEmbeds()).setComponents(buildActionRow()).queue();
                }
            }
        }
    }

    public void editMessage(ButtonInteractionEvent event) {
        MessageEditData response = buildCurrentPageResponse();

        if (getTotalPages() <= 1) {
            event.editMessage(response).setComponents().queue();
        } else {
            event.editMessage(response).setComponents(buildActionRow()).queue();
        }
    }

    public boolean handleButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (!componentId.startsWith(componentPrefix)) {
            return false;
        }

        String action = componentId.substring(componentPrefix.length() + 1);

        switch (action) {
            case "first" -> {
                currentPage = 0;
                return true;
            }
            case "prev" -> {
                if (currentPage > 0) {
                    currentPage--;
                }
                return true;
            }
            case "next" -> {
                if (currentPage < getTotalPages() - 1) {
                    currentPage++;
                }
                return true;
            }
            case "last" -> {
                currentPage = getTotalPages() - 1;
                return true;
            }
        }

        return false;
    }

    private MessageEditData buildCurrentPageResponse() {
        List<T> pageItems = getCurrentPageItems();
        return responseBuilder.apply(pageItems);
    }

    private List<T> getCurrentPageItems() {
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        return items.subList(startIndex, endIndex);
    }

    private ActionRow buildActionRow() {
        boolean hasMultiplePages = getTotalPages() > 1;
        boolean isFirstPage = currentPage == 0;
        boolean isLastPage = currentPage == getTotalPages() - 1;

        String pageIndicator = String.format("Page %d/%d", currentPage + 1, getTotalPages());

        return ActionRow.of(
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":first", "⏮️")
                .withDisabled(!hasMultiplePages || isFirstPage),
            Button.of(ButtonStyle.PRIMARY, componentPrefix + ":prev", "◀️")
                .withDisabled(!hasMultiplePages || isFirstPage),
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":page", pageIndicator)
                .withDisabled(true),
            Button.of(ButtonStyle.PRIMARY, componentPrefix + ":next", "▶️")
                .withDisabled(!hasMultiplePages || isLastPage),
            Button.of(ButtonStyle.SECONDARY, componentPrefix + ":last", "⏭️")
                .withDisabled(!hasMultiplePages || isLastPage)
        );
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) items.size() / itemsPerPage);
    }
}