package net.hypixel.nerdbot.discord.config.channel;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketTemplate {

    /**
     * The category ID this template is for (links to TicketCategory.id)
     */
    private String categoryId;

    /**
     * Title displayed at the top of the modal
     */
    private String modalTitle;

    /**
     * List of input fields for the modal (max 5 per Discord limit)
     */
    private List<TicketTemplateField> fields = new ArrayList<>();

    public TicketTemplate(String categoryId, String modalTitle) {
        this.categoryId = categoryId;
        this.modalTitle = modalTitle;
        this.fields = new ArrayList<>();
    }
}