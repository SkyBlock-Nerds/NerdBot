package net.hypixel.nerdbot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.bot.NerdBot;
import net.hypixel.nerdbot.util.Util;
import net.hypixel.nerdbot.util.discord.MessageCache;
import net.hypixel.nerdbot.util.discord.SuggestionCache;
import net.hypixel.nerdbot.util.gson.InstantTypeAdapter;
import net.hypixel.nerdbot.util.gson.UUIDTypeAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.time.Duration;
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
    public static final Cache<String, DiscordUser> USER_CACHE = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10L))
        .scheduler(Scheduler.systemScheduler())
        .removalListener((key, value, cause) -> {
            DiscordUser discordUser = (DiscordUser) value;
            Database database = NerdBotApp.getBot().getDatabase();
            database.upsertDocument(database.getCollection("users", DiscordUser.class), "discordId", discordUser.getDiscordId(), discordUser);
            log.info("Upserted cached user '" + discordUser.getDiscordId() + "' to database! (Cause: " + cause + ")");
        }).build();

    @Getter
    private static final Optional<UUID> hypixelApiKey = Optional.ofNullable(System.getProperty("hypixel.key")).map(Util::toUUID);
    @Getter
    private static SuggestionCache suggestionCache;
    @Getter
    private static MessageCache messageCache;
    @Getter
    private static Bot bot;

    public NerdBotApp() throws IOException {
    }

    public static void main(String[] args) {
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

        log.info("Registering shutdown hook...");

        Thread userSavingTask = new Thread(() -> {
            log.info("Attempting to save " + USER_CACHE.estimatedSize() + " cached users");

            USER_CACHE.asMap().forEach((s, discordUser) -> {
                bot.getDatabase().upsertDocument(bot.getDatabase().getCollection("users", DiscordUser.class), "discordId", discordUser.getDiscordId(), discordUser);
            });

            NerdBotApp.getBot().onEnd();
        });

        Runtime.getRuntime().addShutdownHook(userSavingTask);
    }
}
