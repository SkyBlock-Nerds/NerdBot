package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.UUIDUtils;
import net.hypixel.nerdbot.util.gson.adapter.BadgeTypeAdapter;
import net.hypixel.nerdbot.util.gson.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.util.gson.adapter.UUIDTypeAdapter;
import org.jetbrains.annotations.NotNull;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public class NerdBotApp {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2,
        createThreadFactory()
    );

    private static ThreadFactory createThreadFactory() {
        return new ThreadFactory() {
            private int counter = 0;
            
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r, "nerdbot-worker-" + (++counter));
                thread.setDaemon(true);
                return thread;
            }
        };
    }
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(Badge.class, new BadgeTypeAdapter())
        .create();

    private static Bot bot;

    public NerdBotApp() throws IOException {
    }

    public static void main(String[] args) {
        for (String signal : new String[]{"INT", "TERM"}) {
            Signal.handle(new Signal(signal), sig -> {
                bot.onEnd();
                System.exit(0);
            });
        }

        NerdBot nerdBot = new NerdBot();
        bot = nerdBot;

        log.info("Starting bot...");

        try {
            nerdBot.create(args);
        } catch (LoginException exception) {
            log.error("Failed to log into the bot with the given credentials!");
            System.exit(-1);
        } catch (MongoException exception) {
            log.error("Failed to connect to MongoDB!");
        } catch (RuntimeException exception) {
            log.error("Unexpected runtime error during bot creation!", exception);
            System.exit(-1);
        }

        log.info("Bot created!");
    }

    public static Optional<UUID> getHypixelAPIKey() {
        return Optional.ofNullable(System.getProperty("hypixel.key")).map(UUIDUtils::toUUID);
    }

    public static NerdBot getBot() {
        return (NerdBot) bot;
    }
}
