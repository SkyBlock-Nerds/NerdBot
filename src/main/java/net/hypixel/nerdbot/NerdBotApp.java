package net.hypixel.nerdbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.MongoException;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.json.adapter.BadgeTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.ColorTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.util.json.adapter.UUIDTypeAdapter;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;
import java.awt.Color;
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
        .registerTypeAdapter(Badge.class, new BadgeTypeAdapter())
        .registerTypeAdapter(Color.class, new ColorTypeAdapter())
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
        } catch (LoginException | InvalidTokenException exception) {
            log.error("Failed to log into the bot with the given credentials: " + exception.getMessage());
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
