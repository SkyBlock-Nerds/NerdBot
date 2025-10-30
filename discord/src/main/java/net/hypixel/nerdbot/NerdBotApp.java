package net.hypixel.nerdbot;

import com.mongodb.MongoException;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.discord.NerdBot;
import sun.misc.Signal;

import javax.security.auth.login.LoginException;

@Slf4j
public class NerdBotApp {

    private NerdBotApp() {
    }

    public static void main(String[] args) {
        Bot bot = new NerdBot();
        BotEnvironment.setBot(bot);

        registerShutdownSignals(bot);

        log.info("Starting bot...");

        try {
            bot.create(args);
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

    private static void registerShutdownSignals(Bot bot) {
        for (String signal : new String[]{"INT", "TERM"}) {
            Signal.handle(new Signal(signal), sig -> {
                bot.onEnd();
                System.exit(0);
            });
        }
    }
}