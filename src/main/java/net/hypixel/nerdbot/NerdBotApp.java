package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.cache.MessageCache;
import net.hypixel.nerdbot.cache.SuggestionCache;
import net.hypixel.nerdbot.util.gson.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.util.gson.adapter.UUIDTypeAdapter;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class NerdBotApp {

    public static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .create();

    @Getter
    private static SuggestionCache suggestionCache;
    @Getter
    private static MessageCache messageCache;
    @Getter
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
            log.info("Attempting to create bot...");
            nerdBot.create(args);
            messageCache = new MessageCache();
            suggestionCache = new SuggestionCache();
            log.info("Bot created!");
        } catch (LoginException e) {
            log.error("Failed to find login for bot!");
            System.exit(-1);
        } catch (Exception exception) {
            log.error("Failed to create bot!");
            exception.printStackTrace();
            System.exit(-1);
        }
    }

    public static Optional<UUID> getHypixelAPIKey() {
        return Optional.ofNullable(System.getProperty("hypixel.key")).map(Util::toUUID);
    }
}
