package net.hypixel.skyblocknerds.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import net.hypixel.skyblocknerds.api.gson.SerializedPathTypeAdapterFactory;
import net.hypixel.skyblocknerds.api.gson.UUIDTypeAdapter;
import net.hypixel.skyblocknerds.api.http.hypixel.HypixelAPIClient;
import net.hypixel.skyblocknerds.api.http.hypixel.IHypixelRequest;
import net.hypixel.skyblocknerds.api.http.mojang.api.IMojangAPIRequest;
import net.hypixel.skyblocknerds.api.http.mojang.api.MojangAPIClient;
import net.hypixel.skyblocknerds.api.http.mojang.sessionserver.IMojangSessionServerRequest;
import net.hypixel.skyblocknerds.api.http.mojang.sessionserver.MojangSessionServerClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.UUID;

public class SkyBlockNerdsAPI {

    public static final Options CLI_OPTIONS = new Options()
            .addRequiredOption("env", "environment", true, "The environment the bot is running in")
            .addRequiredOption("token", "discordToken", true, "The Discord bot token")
            .addOption("mongoUri", true, "The MongoDB connection string")
            .addOption("sqlUri", true, "The PostgreSQL connection string")
            .addOption("hypixelApiKey", true, "The Production Hypixel API key")
            .addOption("readOnly", false, "Whether the bot should run in read-only mode");
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapterFactory(new SerializedPathTypeAdapterFactory())
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .create();
    public static final IMojangAPIRequest MOJANG_REQUEST = new MojangAPIClient().build(IMojangAPIRequest.class);
    public static final IMojangSessionServerRequest MOJANG_SESSION_SERVER_REQUEST = new MojangSessionServerClient().build(IMojangSessionServerRequest.class);
    public static IHypixelRequest HYPIXEL_REQUEST = new HypixelAPIClient().build(IHypixelRequest.class);

    @Getter
    private static CommandLine commandLine;

    public static void parseProgramArguments(String[] args) throws ParseException {
        CommandLineParser commandParser = new DefaultParser();
        commandLine = commandParser.parse(SkyBlockNerdsAPI.CLI_OPTIONS, args);
    }
}