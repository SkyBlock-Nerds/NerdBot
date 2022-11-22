package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetNamesCommand extends ApplicationCommand {

    private final String regex = "^[a-zA-Z0-9_]{2,16}$";
    private final Pattern pattern = Pattern.compile(regex);

    @JDASlashCommand(name = "getnames", subcommand = "nerds", description = "Get a list of all Minecraft names/UUIDs from Nerd roles in the server", defaultLocked = true)
    public void getNerdNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        Guild guild = event.getGuild();
        JsonArray array = getNames(guild, true);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(name = "getnames", subcommand = "all", description = "Get a list of all Minecraft names/UUIDs from members in the server", defaultLocked = true)
    public void getEveryonesNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        Guild guild = event.getGuild();
        JsonArray array = getNames(guild, false);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    private JsonArray getNames(Guild guild, boolean nerdsOnly) {
        JsonArray jsonArray = new JsonArray();

        guild.loadMembers(member -> {
            if (nerdsOnly) {
                if (!Util.hasRole(member, "Nerd") || Util.hasRole(member, "HPC") || Util.hasRole(member, "Grape")) {
                    return;
                }
            }

            if (Util.hasRole(member, "Ultimate Nerd") || Util.hasRole(member, "Ultimate Nerd but Red") || Util.hasRole(member, "Game Master")) {
                NerdBotApp.LOGGER.info("Skipping " + member.getEffectiveName() + " because they have a special role!");
                return;
            }

            Matcher matcher = pattern.matcher(member.getEffectiveName());
            while (matcher.find()) {
                NerdBotApp.LOGGER.info("Found match: " + matcher.group(0));

                try {
                    String response = sendRequest(member.getEffectiveName());
                    JsonObject obj = NerdBotApp.GSON.fromJson(response, JsonObject.class);
                    jsonArray.add(obj.get("uuid").getAsString());
                } catch (IOException | URISyntaxException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return jsonArray;
    }

    private String sendRequest(String name) throws IOException, URISyntaxException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format("https://api.ashcon.app/mojang/v2/user/%s", name))).GET().build();

        try {
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
