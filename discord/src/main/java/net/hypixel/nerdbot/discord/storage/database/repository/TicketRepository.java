package net.hypixel.nerdbot.discord.storage.database.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Sorts;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.repository.Repository;
import org.bson.Document;

import java.util.List;
import java.util.Optional;

/**
 * Database repository that provides helper queries for ticket entities,
 * such as finding by thread, owner, status, and date ranges.
 */
public class TicketRepository extends Repository<Ticket> {

    public TicketRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "tickets", "ticketNumber");
    }

    @Override
    protected String getId(Ticket entity) {
        return String.valueOf(entity.getTicketNumber());
    }

    /**
     * Gets the next available ticket number
     */
    public int getNextTicketNumber() {
        Document doc = getMongoCollection()
            .find()
            .sort(Sorts.descending("ticketNumber"))
            .limit(1)
            .first();

        if (doc == null) {
            return 1;
        }

        Ticket highest = documentToEntity(doc);
        return highest.getTicketNumber() + 1;
    }

    /**
     * Find ticket by Discord thread ID
     */
    public Optional<Ticket> findByThreadId(String threadId) {
        return getAll().stream()
            .filter(t -> threadId.equals(t.getThreadId()))
            .findFirst();
    }

    /**
     * Find all open tickets for a user
     */
    public List<Ticket> findOpenTicketsByUser(String userId) {
        return getAll().stream()
            .filter(t -> userId.equals(t.getOwnerId()))
            .filter(Ticket::isOpen)
            .toList();
    }

    /**
     * Find all tickets for a user
     */
    public List<Ticket> findAllTicketsByUser(String userId) {
        return getAll().stream()
            .filter(t -> userId.equals(t.getOwnerId()))
            .toList();
    }

    /**
     * Find tickets by status ID
     */
    public List<Ticket> findByStatusId(String statusId) {
        return getAll().stream()
            .filter(t -> statusId.equalsIgnoreCase(t.getStatusId()))
            .toList();
    }

    /**
     * Find tickets by category ID
     */
    public List<Ticket> findByCategory(String categoryId) {
        return getAll().stream()
            .filter(t -> categoryId.equalsIgnoreCase(t.getCategoryId()))
            .toList();
    }

    /**
     * Find tickets within date range
     */
    public List<Ticket> findByDateRange(long fromTimestamp, long toTimestamp) {
        return getAll().stream()
            .filter(t -> t.getCreatedAt() >= fromTimestamp && t.getCreatedAt() <= toTimestamp)
            .toList();
    }

    /**
     * Search tickets by keyword in messages
     */
    public List<Ticket> searchByKeyword(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return getAll().stream()
            .filter(t -> t.getMessages() != null && t.getMessages().stream()
                .anyMatch(m -> m.getContent().toLowerCase().contains(lowerKeyword)))
            .toList();
    }

    /**
     * Count open tickets for a user
     */
    public int countOpenTicketsByUser(String userId) {
        return findOpenTicketsByUser(userId).size();
    }
}