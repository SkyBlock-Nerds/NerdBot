package net.hypixel.skyblocknerds.api.cache.suggestion;

import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.redis.RedisCache;

public class SuggestionCache extends RedisCache {

    public SuggestionCache(String uri) {
        super(uri, "suggestion");
    }

    /**
     * Insert a suggestion into the cache.
     *
     * @param suggestionId The Message ID of the suggestion.
     * @param suggestion   The {@link Suggestion suggestion} object.
     */
    public void insertSuggestion(String suggestionId, Suggestion suggestion) {
        set(suggestionId, SkyBlockNerdsAPI.GSON.toJson(suggestion));
    }

    /**
     * Retrieve a {@link Suggestion} from the cache.
     *
     * @param suggestionId The Message ID of the suggestion.
     *
     * @return The {@link Suggestion suggestion} object.
     */
    public Suggestion getSuggestion(String suggestionId) {
        return SkyBlockNerdsAPI.GSON.fromJson(super.get(suggestionId), Suggestion.class);
    }

    /**
     * Delete a suggestion from the cache.
     *
     * @param suggestionId The Message ID of the suggestion.
     */
    public void deleteSuggestion(String suggestionId) {
        delete(suggestionId);
    }
}
