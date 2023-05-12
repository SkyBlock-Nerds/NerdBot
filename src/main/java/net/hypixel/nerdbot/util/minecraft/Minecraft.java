package net.hypixel.nerdbot.util.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;

import javax.annotation.Nullable;
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
public class Minecraft {

    private static final String REGEX = "^[a-zA-Z0-9_]{2,16}";
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";

    private static final Queue<String> usernameQueue = new LinkedList<>();

    /**
     * Fetch a list of UUIDs from the Minecraft API
     *
     * @param guild The guild to load members from
     * @param role  The role to fetch UUIDs for
     * @return A list of UUIDs relating to the specified role
     */
    public static JsonArray getUUIDs(Guild guild, Role role) {
        List<Member> members = guild.getMembersWithRoles(role);

        log.info("Found " + members.size() + " members meeting requirements");
        for (Member member : members) {
            String memberMCUsername = getName(member);

            // checks if there was a valid match found
            if (memberMCUsername != null) {
                log.info("Found match: " + memberMCUsername);
                usernameQueue.add(memberMCUsername);
                log.info(String.format("Added %s (%s) to the username lookup queue!", member.getEffectiveName(), memberMCUsername));
            } else {
                log.info(String.format("Didn't add %s to the username lookup queue", member.getEffectiveName()));
            }
        }

        JsonArray jsonArray = new JsonArray();
        while (!usernameQueue.isEmpty()) {
            String username = usernameQueue.poll();

            String uuid = getUUID(username);
            if (uuid == null) {
                log.info("Skipping over " + username + "!");
                continue;
            }

            jsonArray.add(uuid);
        }

        return jsonArray;
    }

    /**
     * Get a Minecraft username from a Discord username with flare
     *
     * @param member The member to get the Minecraft username of
     * @return The Minecraft username of the target member
     */
    @Nullable
    public static String getName(Member member) {
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

        return memberMCUsername;
    }

    /**
     * Fetch the UUID for a Minecraft player from their username
     *
     * @param username The username to query with
     * @return The UUID of the target user or null if they do not exist or the API fails to return
     */
    @Nullable
    public static String getUUID(String username) {
        String response = requestUserData(username);

        JsonObject obj = NerdBotApp.GSON.fromJson(response, JsonObject.class);
        if (obj == null || obj.get("id") == null) {
            return null;
        }

        return obj.get("id").getAsString();
    }

    private static String requestUserData(String name) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format("https://api.mojang.com/users/profiles/minecraft/%s", name))).GET().build();

        try {
            log.info("Sending request to " + httpRequest.uri());
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException | IOException e) {
            log.error(String.format("Encountered error while looking up Minecraft account of %s!", name), e);
        }

        return null;
    }

}
