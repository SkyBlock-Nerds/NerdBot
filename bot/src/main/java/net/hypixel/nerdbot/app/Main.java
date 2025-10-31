package net.hypixel.nerdbot.app;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.core.api.bot.Bot;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;

/**
 * Main entrypoint for the SkyBlock Nerds Discord bot application.
 */
@Slf4j
public class Main {

    public static void main(String[] args) {
        log.info("Initializing SkyBlock Nerds Discord Bot...");

        SkyBlockNerdsBot bot = new SkyBlockNerdsBot();
        BotEnvironment.setBot(bot);

        registerShutdownHooks(bot);

        log.info("Starting bot...");

        try {
            bot.create(args);
            log.info("Bot successfully started!");
        } catch (LoginException exception) {
            log.error("Failed to authenticate with Discord! Check your bot token.", exception);
            System.exit(1);
        } catch (MongoException exception) {
            log.error("Failed to connect to MongoDB! Check your database configuration.", exception);
            System.exit(1);
        } catch (RuntimeException exception) {
            log.error("Unexpected runtime error during bot startup!", exception);
            System.exit(1);
        }
    }

    private static void registerShutdownHooks(Bot bot) {
        for (String signal : new String[]{"INT", "TERM"}) {
            Signal.handle(new Signal(signal), sig -> {
                log.info("Received shutdown signal: {}", signal);
                bot.onEnd();
                System.exit(0);
            });
        }
    }
}
