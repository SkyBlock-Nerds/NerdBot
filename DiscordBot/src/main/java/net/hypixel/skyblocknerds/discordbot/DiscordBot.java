package net.hypixel.skyblocknerds.discordbot;

import com.freya02.botcommands.api.CommandsBuilder;
import com.freya02.botcommands.api.components.DefaultComponentManager;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.badge.BadgeManager;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.environment.Environment;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.database.mongodb.MongoDB;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.database.sql.DiscordComponentDatabase;
import net.hypixel.skyblocknerds.discordbot.cache.EmojiCache;
import net.hypixel.skyblocknerds.discordbot.configuration.BotConfiguration;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.discordbot.feature.AutomaticCuratorFeature;
import net.hypixel.skyblocknerds.discordbot.feature.MinecraftProfileUpdateFeature;
import net.hypixel.skyblocknerds.discordbot.listener.MemberListener;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import sun.misc.Signal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Getter
@Log4j2
public class DiscordBot {

    private final List<Feature> features = new ArrayList<>();
    @Getter
    private static JDA jda;
    private BotConfiguration botConfiguration;
    private Environment environment;
    @Getter
    private static CommandLine commandLine;

    public void start(String[] args) throws ParseException, InterruptedException {
        CommandLineParser commandParser = new DefaultParser();
        commandLine = commandParser.parse(SkyBlockNerdsAPI.CLI_OPTIONS, args);
        checkForRequiredOptions(commandLine, "env", "discordToken");

        environment = Environment.valueOf(commandLine.getOptionValue("env"));
        log.info("Loading bot in environment: " + environment);

        botConfiguration = ConfigurationManager.loadConfig(BotConfiguration.class);
        checkForRequiredConfigurationValues(ConfigurationManager.loadConfig(GuildConfiguration.class));

        if (commandLine.hasOption("mongoUri")) {
            RepositoryManager.getInstance().registerRepository(DiscordUserRepository.class, MongoDB.createMongoClient(commandLine.getOptionValue("mongoUri")), "skyblock_nerds_2");
            BadgeManager.loadBadges();
            features.addAll(List.of(new MinecraftProfileUpdateFeature(), new AutomaticCuratorFeature()));
        } else {
            log.warn("MongoDB URI not provided, so no MongoDB repositories will be available!");
        }

        JDABuilder jdaBuilder = JDABuilder.createDefault(commandLine.getOptionValue("discordToken"))
                .setEventManager(new AnnotatedEventManager())
                .setEnabledIntents(EnumSet.allOf(GatewayIntent.class))
                .setDisabledIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(new MemberListener());

        jda = jdaBuilder.build();
        jda.awaitReady();

        EmojiCache.initialize();

        if (!botConfiguration.getCommandPackage().isEmpty()) {
            registerCommands();
        }

        features.forEach(Feature::schedule);

        for (String signal : new String[]{"INT", "TERM"}) {
            Signal.handle(new Signal(signal), sig -> {
                log.info("Shutting down bot...");
                for (Feature feature : features) {
                    feature.onFeatureEnd();
                }
                System.exit(0);
            });
        }
    }

    /**
     * Checks for required options, throwing an exception if they are not found
     *
     * @param cmd     The {@link CommandLine} object to check
     * @param options The required options to check for
     */
    private void checkForRequiredOptions(CommandLine cmd, String... options) {
        for (String option : options) {
            if (!cmd.hasOption(option)) {
                throw new IllegalArgumentException("Missing required option: " + option);
            }
        }
    }

    /**
     * Checks for required configuration values, throwing an exception if they are not found
     *
     * @param configuration The {@link BotConfiguration} class to check
     */
    private void checkForRequiredConfigurationValues(GuildConfiguration configuration) {
        if (botConfiguration == null) {
            throw new IllegalArgumentException("Bot configuration cannot be found");
        }

        if (configuration.getPrimaryGuildId().isEmpty()) {
            throw new IllegalArgumentException("Primary guild ID must be specified");
        }
    }

    /**
     * Registers the commands for the bot
     */
    private void registerCommands() {
        CommandsBuilder builder = CommandsBuilder.newBuilder().addOwners(botConfiguration.getOwnerIds().stream().mapToLong(Long::parseLong).toArray());

        log.info("Registering commands from package: " + botConfiguration.getCommandPackage());

        if (commandLine.hasOption("sqlUri")) {
            builder.setComponentManager(new DefaultComponentManager(() -> {
                try {
                    return new DiscordComponentDatabase().getConnection();
                } catch (SQLException e) {
                    log.warn("Failed to initialize SQL connection for command components! This feature will NOT work!");
                }
                return null;
            }));
        } else {
            log.warn("PostgreSQL URI not provided, so command components will NOT function correctly!");
        }

        builder.build(jda, botConfiguration.getCommandPackage());
    }
}