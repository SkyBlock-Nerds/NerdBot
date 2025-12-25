package net.hypixel.nerdbot.discord.config.channel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TicketTemplateField {

    /**
     * Field ID for the modal input (used to retrieve values)
     */
    private String id;

    /**
     * Label displayed above the input field
     */
    private String label;

    /**
     * Placeholder text shown when the field is empty
     */
    private String placeholder;

    /**
     * Whether this field is required
     */
    private boolean required = true;

    /**
     * Input style: "SHORT" for single line, "PARAGRAPH" for multi-line
     */
    private String style = "PARAGRAPH";

    /**
     * Minimum character length
     */
    private int minLength = 1;

    /**
     * Maximum character length
     */
    private int maxLength = 1_000;

    public TicketTemplateField(String id, String label, String placeholder, boolean required, String style) {
        this.id = id;
        this.label = label;
        this.placeholder = placeholder;
        this.required = required;
        this.style = style;
    }
}