package net.hypixel.nerdbot.discord.storage.database.model.ticket;

/**
 * Statistics about the channel index and caches.
 */
public record ChannelIndexStats(
    int indexSize,
    int ticketsWithChannelId,
    int totalTickets,
    int openTicketCacheSize,
    int closedTicketCacheSize,
    boolean indexLoaded
) {

    /**
     * Check if the index size matches the expected count from MongoDB.
     */
    public boolean isInSync() {
        return indexSize == ticketsWithChannelId;
    }

    /**
     * Get the difference between expected and actual index size.
     */
    public int getSyncDifference() {
        return ticketsWithChannelId - indexSize;
    }
}