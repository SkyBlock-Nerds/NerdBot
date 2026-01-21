package net.hypixel.nerdbot.discord.storage.database.model.ticket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single message exchanged within a ticket conversation
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage {

    private String messageId;
    private String authorId;
    private String authorName;
    private String content;
    private List<String> attachmentUrls = new ArrayList<>();
    private long timestamp;
    private boolean staff;

    public TicketMessage(String authorId, String authorName, String content, boolean staff) {
        this.authorId = authorId;
        this.authorName = authorName;
        this.content = content;
        this.staff = staff;
        this.timestamp = System.currentTimeMillis();
        this.attachmentUrls = new ArrayList<>();
    }
}