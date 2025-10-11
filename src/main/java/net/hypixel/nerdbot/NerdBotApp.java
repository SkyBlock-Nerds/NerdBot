package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.MongoException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.json.adapter.BadgeTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.ColorTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.UUIDTypeAdapter;
import net.hypixel.nerdbot.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;
import java.awt.Color;
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
    
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(Badge.class, new BadgeTypeAdapter())
        .registerTypeAdapter(Color.class, new ColorTypeAdapter())
        .create();

    private static Bot bot;

    public NerdBotApp() throws IOException {
    }

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
        } catch (LoginException | InvalidTokenException exception) {
            log.error("Failed to log into the bot with the given credentials: " + exception.getMessage());
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
