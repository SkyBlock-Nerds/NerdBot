package net.hypixel.nerdbot.discord;

import com.google.gson.Gson;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.hypixel.nerdbot.discord.storage.DataSerialization;
import net.hypixel.nerdbot.core.UUIDUtils;
import net.hypixel.nerdbot.discord.api.bot.Bot;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BotEnvironment {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2,
        createThreadFactory()
    );

    public static final Gson GSON = DataSerialization.GSON;

    private static volatile Bot bot;

    public static void setBot(@NotNull Bot botInstance) {
        bot = botInstance;
    }

    public static Bot getBot() {
        if (bot == null) {
            throw new IllegalStateException("Bot instance not initialised yet.");
        }
        return bot;
    }

    public static <T extends Bot> T getBot(@NotNull Class<T> type) {
        Bot botInstance = getBot();

        if (!type.isInstance(botInstance)) {
            throw new IllegalStateException("Bot instance is not of type " + type.getName());
        }

        return type.cast(botInstance);
    }

    public static Optional<UUID> getHypixelAPIKey() {
        return Optional.ofNullable(System.getProperty("hypixel.key")).map(UUIDUtils::toUUID);
    }

    private static ThreadFactory createThreadFactory() {
        return new ThreadFactory() {
            private int counter = 0;

            @Override
            public Thread newThread(@NotNull Runnable runnable) {
                Thread thread = new Thread(runnable, "nerdbot-worker-" + (++counter));
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
