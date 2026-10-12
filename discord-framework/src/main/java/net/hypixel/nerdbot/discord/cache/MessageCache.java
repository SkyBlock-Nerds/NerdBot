package net.hypixel.nerdbot.discord.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Recent messages, so a deleted or edited message can still be shown as it was. It caches what's
 * sent; whatever handles deletes and edits takes the old copy out with {@link #remove} or
 * {@link #replace}, which also means it always sees the old copy whatever order listeners run in.
 */
@Slf4j
public class MessageCache {

    private final Cache<String, Message> cache = Caffeine.newBuilder()
        .maximumSize(5_000)
        .expireAfterWrite(3, TimeUnit.DAYS)
        .expireAfterAccess(1, TimeUnit.DAYS)
        .removalListener((key, value, cause) ->
            log.debug("Message {} removed from cache due to {}", key, cause))
        .recordStats()
        .build();

    public Optional<Message> get(String messageId) {
        return Optional.ofNullable(cache.getIfPresent(messageId));
    }

    /** Takes a deleted message out, returning it as it was. */
    public Optional<Message> remove(String messageId) {
        return Optional.ofNullable(cache.asMap().remove(messageId));
    }

    /** Stores the edited message, returning the copy from before the edit. */
    public Optional<Message> replace(Message edited) {
        return Optional.ofNullable(cache.asMap().replace(edited.getId(), edited));
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        cache.put(event.getMessage().getId(), event.getMessage());
    }

    @SubscribeEvent
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        event.getMessageIds().forEach(cache.asMap().keySet()::remove);
    }
}
