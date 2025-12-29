package net.hypixel.nerdbot.discord.storage.database.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.ChannelIndexStats;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.Ticket;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketReservationResult;
import net.hypixel.nerdbot.discord.storage.database.model.ticket.TicketStatus;
import net.hypixel.nerdbot.discord.storage.repository.Repository;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Repository for ticket entities with caching for open tickets and channel ID lookups.
 */
@Slf4j
public class TicketRepository extends Repository<Ticket> {

    private static final int MAX_OPEN_TICKET_CACHE_SIZE = 500;
    private static final int CLOSED_TICKET_CACHE_DURATION_MINUTES = 5;
    private static final String COUNTER_ID = "ticketNumber";

    private static final Map<String, Boolean> indexCreationStatus = new ConcurrentHashMap<>();

    /**
     * Lightweight index mapping channel IDs to ticket numbers.
     * This avoids loading full ticket objects just to find by channel.
     */
    private final Map<String, String> channelIdIndex = new ConcurrentHashMap<>();

    /**
     * Short-lived cache for recently accessed closed tickets.
     * Entries expire after a few minutes since closed tickets are rarely accessed.
     */
    private final Cache<@NotNull String, Ticket> closedTicketCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(CLOSED_TICKET_CACHE_DURATION_MINUTES, TimeUnit.MINUTES)
        .build();

    /**
     * Counter collection for ticket number generation.
     */
    private final MongoCollection<Document> countersCollection;

    private volatile boolean channelIndexLoaded;

    public TicketRepository(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName, "tickets", "ticketNumber");
        this.countersCollection = mongoClient.getDatabase(databaseName).getCollection("counters");
        ensureIndexes();
    }

    @Override
    protected String getId(Ticket entity) {
        return String.valueOf(entity.getTicketNumber());
    }

    /**
     * Override saveToDatabase to use integer type for ticketNumber filter.
     * The base Repository uses String which causes type mismatch with MongoDB's integer storage,
     * resulting in duplicate documents being created instead of updates.
     */
    @Override
    public com.mongodb.client.result.UpdateResult saveToDatabase(Ticket ticket) {
        Document document = entityToDocument(ticket);
        org.bson.conversions.Bson updateOperation = new Document("$set", document);

        // Use integer for ticketNumber filter to match MongoDB's storage type
        return getMongoCollection().updateOne(
            Filters.eq("ticketNumber", ticket.getTicketNumber()),
            updateOperation,
            new com.mongodb.client.model.UpdateOptions().upsert(true)
        );
    }

    /**
     * Override deleteFromDatabase to use integer type for ticketNumber filter.
     */
    @Override
    public com.mongodb.client.result.DeleteResult deleteFromDatabase(String id) {
        getCache().invalidate(id);
        log.debug("Deleting ticket with ID {} from database", id);

        // Use integer for ticketNumber filter to match MongoDB's storage type
        return getMongoCollection().deleteOne(Filters.eq("ticketNumber", Integer.parseInt(id)));
    }

    /**
     * Override findById to use integer type for ticketNumber filter.
     */
    @Override
    public Ticket findById(String id) {
        Ticket cachedObject = getCache().getIfPresent(id);
        if (cachedObject != null) {
            log.debug("Found ticket with ID {} in cache", id);
            return cachedObject;
        }

        // Also check closed ticket cache
        Ticket closedTicket = closedTicketCache.getIfPresent(id);
        if (closedTicket != null) {
            log.debug("Found ticket with ID {} in closed cache", id);
            return closedTicket;
        }

        // Use integer for ticketNumber filter to match MongoDB's storage type
        Document document = getMongoCollection().find(Filters.eq("ticketNumber", Integer.parseInt(id))).first();
        if (document != null) {
            Ticket ticket = documentToEntity(document);
            cacheTicketAppropriately(ticket);
            return ticket;
        }

        return null;
    }

    /**
     * Gets the next available ticket number using atomic increment on the counter collection.
     * This is thread-safe and works across multiple bot instances.
     *
     * @return the next available ticket number
     */
    public int getNextTicketNumber() {
        Document counter = countersCollection.findOneAndUpdate(
            Filters.eq("_id", COUNTER_ID),
            Updates.inc("seq", 1),
            new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER)
        );

        if (counter == null) {
            throw new IllegalStateException("Failed to get next ticket number from counter collection");
        }

        int nextNumber = counter.getInteger("seq", 1);
        log.debug("Generated ticket number {} from counter collection", nextNumber);
        return nextNumber;
    }

    /**
     * Delete all tickets and reset the counter.
     * WARNING: This is destructive and should only be used for testing/development.
     *
     * @return the number of tickets deleted
     */
    public long deleteAllTicketsAndResetCounter() {
        // Delete all tickets
        long deletedCount = getMongoCollection().deleteMany(new Document()).getDeletedCount();

        // Reset the counter to 0
        countersCollection.deleteOne(Filters.eq("_id", COUNTER_ID));

        // Clear caches
        channelIdIndex.clear();
        closedTicketCache.invalidateAll();
        getCache().invalidateAll();

        log.warn("Deleted {} tickets and reset counter (destructive operation)", deletedCount);
        return deletedCount;
    }

    /**
     * Initialize the counter collection from existing tickets if needed.
     * Call this on startup to ensure the counter is at least as high as the highest existing ticket.
     */
    public void initializeCounterFromExistingTickets() {
        // Find the highest existing ticket number
        Document doc = getMongoCollection()
            .find()
            .sort(Sorts.descending("ticketNumber"))
            .limit(1)
            .first();

        int highestExisting = 0;
        if (doc != null) {
            Object ticketNumber = doc.get("ticketNumber");
            if (ticketNumber instanceof Number) {
                highestExisting = ((Number) ticketNumber).intValue();
            }
        }

        // Get current counter value
        Document counter = countersCollection.find(Filters.eq("_id", COUNTER_ID)).first();
        int currentCounter = counter != null ? counter.getInteger("seq", 0) : 0;

        // Only update if existing tickets are higher than counter
        if (highestExisting > currentCounter) {
            countersCollection.findOneAndUpdate(
                Filters.eq("_id", COUNTER_ID),
                Updates.set("seq", highestExisting),
                new FindOneAndUpdateOptions().upsert(true)
            );
            log.info("Initialized ticket counter to {} (from existing tickets)", highestExisting);
        } else {
            log.debug("Ticket counter already at {} (highest existing: {})", currentCounter, highestExisting);
        }
    }

    /**
     * Find ticket by Discord thread ID, backed by a lightweight map so we do not need to load every ticket.
     * Uses a tiered cache strategy: thread index -> open ticket cache -> closed ticket cache -> database.
     *
     * @param channelId the Discord thread ID
     * @return optional containing the ticket if found
     */
    public Optional<Ticket> findByChannelId(String channelId) {
        if (channelId == null || channelId.isEmpty()) {
            return Optional.empty();
        }

        ensureChannelIndexLoaded();

        log.debug("Looking up ticket for thread {}", channelId);

        String ticketId = channelIdIndex.get(channelId);
        if (ticketId != null) {
            // Try open ticket cache first (most common case)
            Ticket cachedTicket = findById(ticketId);
            if (cachedTicket != null) {
                log.debug("Found open ticket {} in cache for thread {}", cachedTicket.getFormattedTicketId(), channelId);
                return Optional.of(cachedTicket);
            }

            // Try closed ticket cache
            Ticket closedTicket = closedTicketCache.getIfPresent(ticketId);
            if (closedTicket != null) {
                log.debug("Found closed ticket {} in cache for thread {}", closedTicket.getFormattedTicketId(), channelId);
                return Optional.of(closedTicket);
            }

            // Remove stale mapping and fall back to Mongo query
            channelIdIndex.remove(channelId, ticketId);
        }

        Ticket fromDatabase = fetchTicketByChannelId(channelId);
        if (fromDatabase == null) {
            log.debug("No ticket found in database for thread {}", channelId);
            return Optional.empty();
        }

        log.debug("Fetched ticket {} from database for thread {}", fromDatabase.getFormattedTicketId(), channelId);
        cacheTicketAppropriately(fromDatabase);
        return Optional.of(fromDatabase);
    }

    /**
     * Cache a ticket in the appropriate cache based on its state.
     * Open tickets go to the main cache, closed tickets go to the closed cache.
     *
     * @param ticket the ticket to cache
     */
    private void cacheTicketAppropriately(Ticket ticket) {
        indexTicketChannel(ticket);
        if (ticket.isClosed()) {
            closedTicketCache.put(String.valueOf(ticket.getTicketNumber()), ticket);
        } else {
            cacheObject(ticket);
        }
    }

    /**
     * Load the thread index from MongoDB while only storing lightweight channelId -> ticketNumber pairs.
     * This is a lightweight operation that only loads two fields per ticket.
     */
    public synchronized void loadChannelIndex() {
        if (channelIndexLoaded) {
            return;
        }

        log.debug("Building ticket thread index...");
        int count = 0;
        for (Document document : getMongoCollection()
            .find(new Document("channelId", new Document("$exists", true)))
            .projection(Projections.include("channelId", "ticketNumber"))) {

            String channelId = document.getString("channelId");
            Object ticketNumber = document.get("ticketNumber");

            if (channelId != null && ticketNumber != null) {
                channelIdIndex.put(channelId, ticketNumber.toString());
                count++;
            }
        }

        channelIndexLoaded = true;
        log.info("Loaded {} ticket thread references into memory", count);
    }

    /**
     * Force a refresh of the thread index from MongoDB.
     * Clears the existing index and rebuilds it from the database.
     * Use this when the index may be out of sync with the database.
     *
     * @return the number of entries in the refreshed index
     */
    public synchronized int refreshChannelIndex() {
        log.info("Refreshing thread index from MongoDB...");

        // Clear existing index
        channelIdIndex.clear();
        channelIndexLoaded = false;

        // Rebuild
        int count = 0;
        for (Document document : getMongoCollection()
            .find(new Document("channelId", new Document("$exists", true)))
            .projection(Projections.include("channelId", "ticketNumber"))) {

            String channelId = document.getString("channelId");
            Object ticketNumber = document.get("ticketNumber");

            if (channelId != null && !channelId.isEmpty() && ticketNumber != null) {
                channelIdIndex.put(channelId, ticketNumber.toString());
                count++;
            }
        }

        channelIndexLoaded = true;
        log.info("Thread index refreshed: {} entries loaded", count);
        return count;
    }

    private void ensureChannelIndexLoaded() {
        if (!channelIndexLoaded) {
            loadChannelIndex();
        }
    }

    @Nullable
    private Ticket fetchTicketByChannelId(String channelId) {
        Document document = getMongoCollection()
            .find(Filters.eq("channelId", channelId))
            .first();

        if (document == null) {
            return null;
        }

        Ticket ticket = documentToEntity(document);
        channelIdIndex.put(channelId, String.valueOf(ticket.getTicketNumber()));
        log.debug("Indexed ticket {} for thread {}", ticket.getFormattedTicketId(), channelId);
        return ticket;
    }

    private void indexTicketChannel(Ticket ticket) {
        if (ticket.getChannelId() != null && !ticket.getChannelId().isEmpty()) {
            channelIdIndex.put(ticket.getChannelId(), String.valueOf(ticket.getTicketNumber()));
            log.debug("Indexed thread {} -> ticket {}", ticket.getChannelId(), ticket.getFormattedTicketId());
        }
    }

    @Override
    public void cacheObject(String id, Ticket object) {
        super.cacheObject(id, object);
        indexTicketChannel(object);
    }

    /**
     * Creates a MongoDB filter for open tickets.
     * A ticket is open if closedAt doesn't exist, is null, or is <= 0.
     */
    private Bson createOpenTicketFilter() {
        return Filters.or(
            Filters.exists("closedAt", false),
            Filters.eq("closedAt", null),
            Filters.lte("closedAt", 0L)
        );
    }

    /**
     * Load all open tickets into the cache on startup.
     * Respects the maximum cache size limit.
     */
    public synchronized void cacheOpenTickets() {
        log.debug("Caching open tickets...");

        int cached = 0;
        for (Document doc : getMongoCollection()
            .find(createOpenTicketFilter())
            .sort(Sorts.descending("updatedAt"))
            .limit(MAX_OPEN_TICKET_CACHE_SIZE)) {

            Ticket ticket = documentToEntity(doc);
            if (!ticket.isClosed()) {
                cacheObject(ticket);
                cached++;
            }
        }

        log.info("Cached {} open tickets (max: {})", cached, MAX_OPEN_TICKET_CACHE_SIZE);
    }

    /**
     * Find all open tickets for a user.
     * Queries MongoDB directly to avoid loading all tickets into memory.
     *
     * @param userId the Discord user ID
     * @return list of open tickets owned by the user
     */
    public List<Ticket> findOpenTicketsByUser(String userId) {
        Bson filter = Filters.and(
            Filters.eq("ownerId", userId),
            createOpenTicketFilter()
        );

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection().find(filter).sort(Sorts.descending("createdAt"))) {
            Ticket ticket = documentToEntity(doc);
            // Double-check in Java in case of type issues
            if (!ticket.isClosed()) {
                cacheTicketAppropriately(ticket);
                tickets.add(ticket);
            } else {
                // Log when MongoDB filter returns a closed ticket - indicates potential data issue
                log.warn("MongoDB filter returned closed ticket {} for user {} (closedAt={}, status={})",
                    ticket.getFormattedTicketId(), userId, ticket.getClosedAt(), ticket.getStatus());
            }
        }
        return tickets;
    }

    /**
     * Find all tickets for a user (both open and closed).
     * Queries MongoDB directly with pagination support.
     *
     * @param userId the Discord user ID
     * @return list of all tickets owned by the user
     */
    public List<Ticket> findAllTicketsByUser(String userId) {
        return findAllTicketsByUser(userId, 100);
    }

    /**
     * Find all tickets for a user with a limit.
     *
     * @param userId the Discord user ID
     * @param limit  maximum number of tickets to return
     *
     * @return list of tickets owned by the user
     */
    public List<Ticket> findAllTicketsByUser(String userId, int limit) {
        Bson filter = Filters.eq("ownerId", userId);

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.descending("createdAt"))
            .limit(limit)) {

            Ticket ticket = documentToEntity(doc);
            cacheTicketAppropriately(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Find tickets by status.
     * Queries MongoDB directly.
     *
     * @param status the status to filter by
     * @return list of tickets with the specified status
     */
    public List<Ticket> findByStatus(TicketStatus status) {
        return findByStatus(status, 100);
    }

    /**
     * Find tickets by status with a limit.
     *
     * @param status the status to filter by
     * @param limit  maximum number of tickets to return
     *
     * @return list of tickets with the specified status
     */
    public List<Ticket> findByStatus(TicketStatus status, int limit) {
        Bson filter = Filters.eq("status", status.name());

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.descending("updatedAt"))
            .limit(limit)) {

            Ticket ticket = documentToEntity(doc);
            cacheTicketAppropriately(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Find tickets by category ID.
     * Queries MongoDB directly.
     *
     * @param categoryId the category ID to filter by
     * @return list of tickets in the specified category
     */
    public List<Ticket> findByCategory(String categoryId) {
        return findByCategory(categoryId, 100);
    }

    /**
     * Find tickets by category ID with a limit.
     *
     * @param categoryId the category ID to filter by
     * @param limit      maximum number of tickets to return
     *
     * @return list of tickets in the specified category
     */
    public List<Ticket> findByCategory(String categoryId, int limit) {
        Bson filter = Filters.eq("categoryId", categoryId);

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.descending("createdAt"))
            .limit(limit)) {

            Ticket ticket = documentToEntity(doc);
            cacheTicketAppropriately(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Find tickets within a date range.
     * Queries MongoDB directly.
     *
     * @param fromTimestamp start of the date range (inclusive)
     * @param toTimestamp   end of the date range (inclusive)
     * @return list of tickets created within the date range
     */
    public List<Ticket> findByDateRange(long fromTimestamp, long toTimestamp) {
        return findByDateRange(fromTimestamp, toTimestamp, 500);
    }

    /**
     * Find tickets within a date range with a limit.
     *
     * @param fromTimestamp start of the date range (inclusive)
     * @param toTimestamp   end of the date range (inclusive)
     * @param limit         maximum number of tickets to return
     *
     * @return list of tickets created within the date range
     */
    public List<Ticket> findByDateRange(long fromTimestamp, long toTimestamp, int limit) {
        Bson filter = Filters.and(
            Filters.gte("createdAt", fromTimestamp),
            Filters.lte("createdAt", toTimestamp)
        );

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.descending("createdAt"))
            .limit(limit)) {

            Ticket ticket = documentToEntity(doc);
            cacheTicketAppropriately(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Search tickets by keyword in messages.
     * Uses MongoDB text search if available, otherwise performs regex search.
     *
     * @param keyword the keyword to search for
     * @return list of tickets containing the keyword in their messages
     */
    public List<Ticket> searchByKeyword(String keyword) {
        return searchByKeyword(keyword, 100);
    }

    /**
     * Search tickets by keyword in messages with a limit.
     *
     * @param keyword the keyword to search for
     * @param limit   maximum number of tickets to return
     *
     * @return list of tickets containing the keyword in their messages
     */
    public List<Ticket> searchByKeyword(String keyword, int limit) {
        // Use regex search on the messages.content field
        Bson filter = Filters.regex("messages.content", keyword, "i");

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.descending("updatedAt"))
            .limit(limit)) {

            Ticket ticket = documentToEntity(doc);
            cacheTicketAppropriately(ticket);
            tickets.add(ticket);
        }
        return tickets;
    }

    /**
     * Count open tickets for a user.
     * Uses MongoDB count query instead of loading all tickets.
     *
     * @param userId the Discord user ID
     * @return count of open tickets for the user
     */
    public int countOpenTicketsByUser(String userId) {
        Bson filter = Filters.and(
            Filters.eq("ownerId", userId),
            createOpenTicketFilter()
        );

        return (int) getMongoCollection().countDocuments(filter);
    }

    /**
     * Check if a user can create a ticket and reserve a ticket number.
     * Returns a detailed result including current ticket count.
     *
     * @param userId     the Discord user ID
     * @param maxTickets maximum allowed open tickets per user
     *
     * @return reservation result with ticket number or error
     */
    public TicketReservationResult reserveTicketSlot(String userId, int maxTickets) {
        int currentCount = countOpenTicketsByUser(userId);

        if (currentCount >= maxTickets) {
            log.debug("User {} has {} open tickets, max is {}", userId, currentCount, maxTickets);
            return TicketReservationResult.limitReached(currentCount, maxTickets);
        }

        int ticketNumber = getNextTicketNumber();

        log.debug("Reserved ticket number {} for user {} (current open: {}, max: {})",
            ticketNumber, userId, currentCount, maxTickets);

        return TicketReservationResult.success(ticketNumber, currentCount);
    }

    /**
     * Get statistics about the thread index.
     *
     * @return index statistics
     */
    public ChannelIndexStats getChannelIndexStats() {
        ensureChannelIndexLoaded();

        int indexSize = channelIdIndex.size();
        long ticketsWithChannelId = getMongoCollection().countDocuments(new Document("channelId", new Document("$exists", true).append("$ne", "")));
        long totalTickets = getMongoCollection().countDocuments();
        int openCacheSize = (int) getCache().estimatedSize();
        int closedCacheSize = (int) closedTicketCache.estimatedSize();

        return new ChannelIndexStats(
            indexSize,
            (int) ticketsWithChannelId,
            (int) totalTickets,
            openCacheSize,
            closedCacheSize,
            channelIndexLoaded
        );
    }

    /**
     * Ensure all required indexes exist in MongoDB.
     * Creates indexes in background to avoid blocking operations.
     */
    private void ensureIndexes() {
        MongoCollection<Document> collection = getMongoCollection();
        IndexOptions options = new IndexOptions().background(true);

        createIndexIfNotExists(collection, "ownerId", Indexes.ascending("ownerId"), options);
        createIndexIfNotExists(collection, "status", Indexes.ascending("status"), options);
        createIndexIfNotExists(collection, "categoryId", Indexes.ascending("categoryId"), options);
        createIndexIfNotExists(collection, "createdAt", Indexes.ascending("createdAt"), options);
        createIndexIfNotExists(collection, "updatedAt", Indexes.ascending("updatedAt"), options);
        createIndexIfNotExists(collection, "closedAt", Indexes.ascending("closedAt"), options);
        createIndexIfNotExists(collection, "channelId", Indexes.ascending("channelId"), options);
        createIndexIfNotExists(collection, "owner_closed",
            Indexes.compoundIndex(Indexes.ascending("ownerId"), Indexes.ascending("closedAt")), options);
    }

    /**
     * Find all open tickets that need reminder checking.
     * Only loads tickets that are open and in a non-closed status.
     *
     * @return list of open tickets
     */
    public List<Ticket> findOpenTickets() {
        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(createOpenTicketFilter())
            .sort(Sorts.ascending("updatedAt"))) {

            Ticket ticket = documentToEntity(doc);
            if (!ticket.isClosed()) {
                cacheTicketAppropriately(ticket);
                tickets.add(ticket);
            } else {
                // Log when MongoDB filter returns a closed ticket - indicates potential data issue
                log.warn("MongoDB filter returned closed ticket {} (closedAt={}, status={})",
                    ticket.getFormattedTicketId(), ticket.getClosedAt(), ticket.getStatus());
            }
        }
        return tickets;
    }

    /**
     * Find tickets that are stale (in a specific status and not updated for a certain time).
     * Used for auto-close functionality.
     *
     * @param status     the status to filter by
     * @param cutoffTime tickets updated before this time are considered stale
     *
     * @return list of stale tickets
     */
    public List<Ticket> findStaleTickets(TicketStatus status, long cutoffTime) {
        Bson filter = Filters.and(
            Filters.eq("status", status.name()),
            Filters.lt("updatedAt", cutoffTime),
            createOpenTicketFilter()
        );

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection().find(filter)) {
            Ticket ticket = documentToEntity(doc);
            if (!ticket.isClosed()) {
                cacheTicketAppropriately(ticket);
                tickets.add(ticket);
            }
        }
        return tickets;
    }

    /**
     * Find closed tickets that were closed before the specified timestamp.
     * Used for automatic cleanup of old closed tickets.
     *
     * @param closedBefore only return tickets closed before this timestamp
     *
     * @return list of old closed tickets
     */
    public List<Ticket> findClosedTicketsOlderThan(long closedBefore) {
        Bson filter = Filters.and(
            Filters.gt("closedAt", 0L),
            Filters.lt("closedAt", closedBefore)
        );

        List<Ticket> tickets = new ArrayList<>();
        for (Document doc : getMongoCollection()
            .find(filter)
            .sort(Sorts.ascending("closedAt"))) {

            Ticket ticket = documentToEntity(doc);
            if (ticket.isClosed()) {
                tickets.add(ticket);
            }
        }
        return tickets;
    }

    /**
     * Remove a ticket from the thread index.
     * Called when a ticket is deleted.
     *
     * @param channelId the thread ID to remove
     */
    public void removeFromChannelIndex(String channelId) {
        if (channelId != null) {
            channelIdIndex.remove(channelId);
        }
    }

    /**
     * Create an index if it doesn't already exist.
     *
     * @param collection the MongoDB collection
     * @param indexName  a descriptive name for the index
     * @param indexKey   the index definition
     * @param options    index options (e.g., background creation)
     */
    private void createIndexIfNotExists(MongoCollection<Document> collection,
                                        String indexName, Bson indexKey, IndexOptions options) {
        String indexKeyStr = indexKey.toBsonDocument().toJson();
        String cacheKey = indexName + ":" + indexKeyStr;

        if (indexCreationStatus.getOrDefault(cacheKey, false)) {
            return;
        }

        try {
            for (Document idx : collection.listIndexes()) {
                String existingKey = idx.get("key", Document.class).toBsonDocument().toJson();
                if (existingKey.equals(indexKeyStr)) {
                    indexCreationStatus.put(cacheKey, true);
                    log.debug("Index {} already exists", indexName);
                    return;
                }
            }

            collection.createIndex(indexKey, options);
            indexCreationStatus.put(cacheKey, true);
            log.info("Created index {} in background", indexName);
        } catch (Exception e) {
            log.error("Failed to create index {}", indexName, e);
        }
    }

    /**
     * Move a ticket from open cache to closed cache.
     * Called when a ticket is closed.
     *
     * @param ticket the ticket that was closed
     */
    public void onTicketClosed(Ticket ticket) {
        String id = String.valueOf(ticket.getTicketNumber());
        // Remove from open ticket cache
        getCache().invalidate(id);
        // Add to closed ticket cache for short-term access
        closedTicketCache.put(id, ticket);
        log.debug("Moved ticket {} to closed cache", ticket.getFormattedTicketId());
    }

    /**
     * Move a ticket from closed cache to open cache.
     * Called when a ticket is reopened.
     *
     * @param ticket the ticket that was reopened
     */
    public void onTicketReopened(Ticket ticket) {
        String id = String.valueOf(ticket.getTicketNumber());
        // Remove from closed ticket cache
        closedTicketCache.invalidate(id);
        // Add to open ticket cache
        cacheObject(id, ticket);
        log.debug("Moved ticket {} to open cache", ticket.getFormattedTicketId());
    }

    /**
     * Diagnostic method to log ticket statistics.
     * Useful for debugging ticket count discrepancies.
     */
    public void logTicketDiagnostics() {
        log.info("=== TICKET DIAGNOSTICS ===");

        // Count total tickets
        long totalTickets = getMongoCollection().countDocuments();
        log.info("Total tickets in database: {}", totalTickets);

        // Count tickets using the open filter
        long openByFilter = getMongoCollection().countDocuments(createOpenTicketFilter());
        log.info("Open tickets (by MongoDB filter): {}", openByFilter);

        // Count tickets with closedAt > 0 (closed)
        long closedByFilter = getMongoCollection().countDocuments(Filters.gt("closedAt", 0L));
        log.info("Closed tickets (closedAt > 0): {}", closedByFilter);

        // Count tickets with closedAt = 0
        long zeroClosedAt = getMongoCollection().countDocuments(Filters.eq("closedAt", 0L));
        log.info("Tickets with closedAt = 0: {}", zeroClosedAt);

        // Count tickets with closedAt = -1 (reopened)
        long negativeClosedAt = getMongoCollection().countDocuments(Filters.lt("closedAt", 0L));
        log.info("Tickets with closedAt < 0 (reopened): {}", negativeClosedAt);

        // Count tickets with closedAt field missing
        long missingClosedAt = getMongoCollection().countDocuments(Filters.exists("closedAt", false));
        log.info("Tickets with closedAt missing: {}", missingClosedAt);

        // Count tickets with closedAt = null
        long nullClosedAt = getMongoCollection().countDocuments(Filters.eq("closedAt", null));
        log.info("Tickets with closedAt = null: {}", nullClosedAt);

        // Log a sample of "open" tickets to see their actual closedAt values
        log.info("--- Sample of tickets matching open filter ---");
        int sampleCount = 0;
        for (Document doc : getMongoCollection().find(createOpenTicketFilter()).limit(10)) {
            Object closedAt = doc.get("closedAt");
            Integer ticketNum = doc.getInteger("ticketNumber");
            String ownerId = doc.getString("ownerId");
            log.info("  Ticket #{}: closedAt={} (type={}), ownerId={}",
                ticketNum,
                closedAt,
                closedAt != null ? closedAt.getClass().getSimpleName() : "null",
                ownerId);
            sampleCount++;
        }
        log.info("Showed {} sample tickets", sampleCount);

        log.info("=== END DIAGNOSTICS ===");
    }
}