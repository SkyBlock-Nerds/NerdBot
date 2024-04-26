package net.hypixel.skyblocknerds.discordbot;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.environment.Environment;
import net.hypixel.skyblocknerds.api.feature.Feature;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.DiscordUserRepository;
import net.hypixel.skyblocknerds.discordbot.configuration.BotConfiguration;
import net.hypixel.skyblocknerds.discordbot.feature.MinecraftProfileUpdateFeature;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import sun.misc.Signal;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Log4j2
public class DiscordBot {

    private final Options CLI_OPTIONS = new Options()
            .addOption("env", "environment", true, "The environment the bot is running in")
            .addOption("discordToken", true, "The Discord bot token")
            .addOption("mongoUri", true, "The MongoDB connection string")
            .addOption("hypixelApiKey", true, "The Production Hypixel API key")
            .addOption("readOnly", false, "Whether the bot should run in read-only mode");

    @Getter
    private final List<Feature> features = new ArrayList<>();
    @Getter
    private JDA jda;
    @Getter
    private BotConfiguration botConfiguration;
    @Getter
    private Environment environment;

    public void start(String[] args) throws ParseException, InterruptedException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(CLI_OPTIONS, args);

        checkForRequiredOptions(cmd, "env", "discordToken");

        environment = Environment.valueOf(cmd.getOptionValue("env"));
        log.info("Loading bot in environment: " + environment);

        checkForRequiredConfigurationValues(botConfiguration = ConfigurationManager.loadConfig(BotConfiguration.class));

        JDABuilder jdaBuilder = JDABuilder.createDefault(cmd.getOptionValue("discordToken"))
                .setEventManager(new AnnotatedEventManager())
                .setEnabledIntents(EnumSet.allOf(GatewayIntent.class))
                .setDisabledIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING)
                .setMemberCachePolicy(MemberCachePolicy.ALL);

        jda = jdaBuilder.build();
        jda.awaitReady();

        if (cmd.hasOption("mongoUri")) {
            log.info("Registering MongoDB repositories");
            RepositoryManager.getInstance().registerRepository(DiscordUserRepository.class, createMongoClient(cmd.getOptionValue("mongoUri")), "skyblock_nerds");
            features.add(new MinecraftProfileUpdateFeature());
        } else {
            log.warn("MongoDB URI not provided, so not registering MongoDB repositories!");
        }

        features.forEach(Feature::onFeatureStart);

        for (String signal : new String[]{"INT", "TERM"}) {
            Signal.handle(new Signal(signal), sig -> {
                for (Feature feature : features) {
                    feature.onFeatureEnd();
                }
                System.exit(0);
            });
        }
    }

    private MongoClient createMongoClient(String uri) {
        ConnectionString connectionString = new ConnectionString(uri);

        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .codecRegistry(codecRegistry)
                //.applyToServerSettings(builder -> builder.addServerMonitorListener(this))
                .build();

        return MongoClients.create(clientSettings);
    }

    private void checkForRequiredOptions(CommandLine cmd, String... options) {
        for (String option : options) {
            if (!cmd.hasOption(option)) {
                throw new IllegalArgumentException("Missing required option: " + option);
            }
        }
    }

    private void checkForRequiredConfigurationValues(BotConfiguration configuration) {
        if (botConfiguration == null) {
            throw new IllegalArgumentException("Bot configuration cannot be found");
        }

        if (configuration.getPrimaryGuildId() == null) {
            throw new IllegalArgumentException("Primary guild ID must be specified");
        }
    }
}