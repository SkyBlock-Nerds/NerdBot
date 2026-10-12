package net.hypixel.nerdbot.app.nomination;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads TicketBot's {@code tickets} collection directly, so NerdBot doesn't depend on TicketBot's
 * model classes.
 */
@Slf4j
public class MongoOpenTickets implements OpenTickets {

    private final Database database;

    @Inject
    public MongoOpenTickets(Database database) {
        this.database = database;
    }

    @Override
    public List<String> linksFor(String memberId) {
        try {
            if (!database.isConnected() || database.getMongoClient() == null || database.getConnectionString().getDatabase() == null) {
                return List.of();
            }

            MongoCollection<Document> tickets = database.getMongoClient()
                .getDatabase(database.getConnectionString().getDatabase())
                .getCollection("tickets");

            List<String> links = new ArrayList<>();
            for (Document ticket : tickets.find(openTicketsOf(memberId)).sort(Sorts.descending("createdAt"))) {
                Integer ticketNumber = ticket.getInteger("ticketNumber");
                String channelId = ticket.getString("channelId");
                if (ticketNumber != null && channelId != null) {
                    links.add(String.format("#%04d: <#%s>", ticketNumber, channelId));
                }
            }
            return links;
        } catch (RuntimeException exception) {
            log.warn("Failed to look up the open tickets of member {}", memberId, exception);
            return List.of();
        }
    }

    static Bson openTicketsOf(String memberId) {
        return Filters.and(
            Filters.eq("ownerId", memberId),
            Filters.or(
                Filters.exists("closedAt", false),
                Filters.eq("closedAt", null),
                Filters.lte("closedAt", 0L)));
    }
}
