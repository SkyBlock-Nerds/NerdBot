package net.hypixel.nerdbot.discord.storage.database.repository;

import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketMessage;
import net.hypixel.nerdbot.discord.storage.repository.Repository;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TicketMessageArchiveRepository extends Repository<Document> {

    public TicketMessageArchiveRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "ticket_messages_archive", "ticketNumber");
    }

    /**
     * Archive overflow messages for a ticket.
     *
     * @param ticketNumber the ticket number
     * @param overflow     the messages that overflowed the main storage
     */
    public void archiveOverflowMessages(int ticketNumber, List<TicketMessage> overflow) {
        if (overflow == null || overflow.isEmpty()) {
            return;
        }

        Document doc = new Document();
        doc.put("ticketNumber", ticketNumber);
        doc.put("archivedAt", System.currentTimeMillis());
        doc.put("messages", messagesToDocuments(overflow));
        saveToDatabase(doc);

        log.debug("Archived {} overflow messages for ticket {}", overflow.size(), ticketNumber);
    }

    /**
     * Convert TicketMessage objects to MongoDB documents.
     */
    private List<Document> messagesToDocuments(List<TicketMessage> messages) {
        List<Document> docs = new ArrayList<>();
        for (TicketMessage msg : messages) {
            Document doc = new Document();
            doc.put("messageId", msg.getMessageId());
            doc.put("authorId", msg.getAuthorId());
            doc.put("authorName", msg.getAuthorName());
            doc.put("content", msg.getContent());
            doc.put("timestamp", msg.getTimestamp());
            doc.put("staff", msg.isStaff());

            if (msg.getAttachmentUrls() != null && !msg.getAttachmentUrls().isEmpty()) {
                doc.put("attachmentUrls", msg.getAttachmentUrls());
            }

            docs.add(doc);
        }

        return docs;
    }

    @Override
    protected String getId(Document entity) {
        return String.valueOf(entity.getInteger("ticketNumber"));
    }
}