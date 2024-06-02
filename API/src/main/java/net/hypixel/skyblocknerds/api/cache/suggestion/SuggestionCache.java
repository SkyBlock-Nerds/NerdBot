package net.hypixel.skyblocknerds.api.cache.suggestion;

import com.google.gson.JsonObject;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.redis.RedisCache;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class SuggestionCache extends RedisCache {

    public SuggestionCache(URI uri) {
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

    /**
     * Get a {@link List} of {@link Suggestion suggestions} by a User ID.
     *
     * @param authorId The User ID of the author.
     *
     * @return A {@link Optional} containing the {@link List} of {@link Suggestion suggestions}.
     */
    public Optional<List<Suggestion>> getSuggestionsByAuthor(String authorId) {
        List<JsonObject> suggestions = scanForValue("authorId", authorId);

        return Optional.of(suggestions.stream()
            .map(json -> SkyBlockNerdsAPI.GSON.fromJson(json, Suggestion.class))
            .toList());
    }
}
