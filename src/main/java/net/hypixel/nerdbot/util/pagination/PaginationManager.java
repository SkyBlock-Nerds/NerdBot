package net.hypixel.nerdbot.util.pagination;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class PaginationManager {

    private static final Cache<@NotNull String, PaginatedResponse<?>> activePages = Caffeine.newBuilder()
        .expireAfterWrite(15, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build();

    public static void registerPagination(@NotNull String messageId, @NotNull PaginatedResponse<?> pagination) {
        activePages.put(messageId, pagination);
    }

    @Nullable
    public static PaginatedResponse<?> getPagination(@NotNull String messageId) {
        return activePages.getIfPresent(messageId);
    }

    public static void removePagination(@NotNull String messageId) {
        activePages.invalidate(messageId);
    }

    public static boolean handleButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String messageId = event.getMessageId();
        PaginatedResponse<?> pagination = getPagination(messageId);

        if (pagination == null) {
            return false;
        }

        if (pagination.handleButtonInteraction(event)) {
            pagination.editMessage(event);
            return true;
        }

        return false;
    }

    public static void clearCache() {
        activePages.invalidateAll();
    }

    public static long getCacheSize() {
        return activePages.estimatedSize();
    }
}