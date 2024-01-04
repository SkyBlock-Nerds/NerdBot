package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.MongoException;
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
import java.util.*;
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
        } catch (Exception exception) {
            log.error("Failed to create bot!", exception);
            System.exit(-1);
        }

        log.info("Bot created!");
    }

    public static Optional<UUID> getHypixelAPIKey() {
        return Optional.ofNullable(System.getProperty("hypixel.key")).map(Util::toUUID);
    }

    public static NerdBot getBot() {
        return (NerdBot) bot;
    }
}
