package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;

@Log4j2
public class GetNamesCommand extends ApplicationCommand {

    private static final String REGEX = "^[a-zA-Z0-9_]{2,16}";
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    private final Queue<String> usernameQueue = new LinkedList<>();

    @JDASlashCommand(name = "getusernames", subcommand = "nerds", description = "Get a list of all Minecraft names/UUIDs from Nerd roles in the server", defaultLocked = true)
    public void getNerdNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        Guild guild = event.getGuild();
        JsonArray array = getNames(guild, true);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    @JDASlashCommand(name = "getusernames", subcommand = "all", description = "Get a list of all Minecraft names/UUIDs from members in the server", defaultLocked = true)
    public void getEveryonesNames(GuildSlashEvent event) throws IOException {
        event.deferReply(true).queue();
        Guild guild = event.getGuild();
        JsonArray array = getNames(guild, false);
        File file = Util.createTempFile("uuids.txt", NerdBotApp.GSON.toJson(array));
        event.getHook().sendFiles(FileUpload.fromData(file)).queue();
    }

    private JsonArray getNames(Guild guild, boolean nerdsOnly) {
        JsonArray jsonArray = new JsonArray();

        List<Member> members = guild.loadMembers().get()
                .stream()
                .filter(member -> Util.hasRole(member, "Member"))
                .toList();

        if (nerdsOnly) {
            members = members.stream().filter(member -> Util.hasRole(member, "Nerd") || Util.hasRole(member, "HPC") || Util.hasRole(member, "Grape")).toList();
        }

        log.info("Found " + members.size() + " members meeting requirements");
        members.forEach(member -> {
            // removes non-standard ascii characters from the discord nickname
            String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
            String memberMCUsername = null;

            // checks if the member's username has flair
            if (!Pattern.matches(REGEX, plainUsername)) {
                // removes start and end characters ([example], {example}, |example| or (example)).
                // also strips spaces from the username
                plainUsername = plainUsername.replaceAll(SURROUND_REGEX, "").replace(" ", "");
                String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

                // gets the first item that matches the name constraints
                for (String item : splitUsername) {
                    if (Pattern.matches(REGEX, item)) {
                        memberMCUsername = item;
                        break;
                    }
                }
            } else {
                memberMCUsername = plainUsername.replace(" ", "");
            }

            // checks if there was a valid match found
            if (memberMCUsername != null) {
                log.info("Found match: " + memberMCUsername);
                usernameQueue.add(memberMCUsername);
                log.info(String.format("Added %s (%s) to the username lookup queue!", member.getEffectiveName(), memberMCUsername));
            } else {
                log.info(String.format("Didn't add %s to the username lookup queue", member.getEffectiveName()));
            }
        });

        while (!usernameQueue.isEmpty()) {
            try {
                String username = usernameQueue.poll();
                String response = sendRequest(username);
                JsonObject obj = NerdBotApp.GSON.fromJson(response, JsonObject.class);
                if (obj == null || obj.get("id") == null) {
                    log.info("Skipping over " + username + "!");
                    continue;
                }
                jsonArray.add(obj.get("id").getAsString());
            } catch (IOException | URISyntaxException | InterruptedException e) {
                log.error("Encountered an error while looking up UUID: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        return jsonArray;
    }

    private String sendRequest(String name) throws IOException, URISyntaxException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format("https://api.mojang.com/users/profiles/minecraft/%s", name))).GET().build();

        try {
            log.info("Sending request to " + httpRequest.uri());
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            log.error(String.format("Encountered error while looking up Minecraft account of %s!", name));
            e.printStackTrace();
        }

        return null;
    }
}
