package net.hypixel.nerdbot.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.command.ItemGenCommands;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
public class Util {

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final DecimalFormat COMMA_SEPARATED_FORMAT = new DecimalFormat("#,###");

    private Util() {
    }

    public static Stream<String> safeArrayStream(String[]... arrays) {
        Stream<String> stream = Stream.empty();

        if (arrays != null) {
            for (String[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static void sleep(TimeUnit unit, long time) {
        try {
            Thread.sleep(unit.toMillis(time));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Nullable
    public static Guild getGuild(String guildId) {
        return NerdBotApp.getBot().getJDA().getGuildById(guildId);
    }

    public static boolean hasRole(Member member, String name) {
        List<Role> roles = member.getRoles();
        return roles.stream().anyMatch(role -> role.getName().equalsIgnoreCase(name));
    }

    @Nullable
    public static Role getRole(String name) {
        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        if (guild == null) {
            return null;
        }
        return guild.getRoles().stream().filter(role -> role.getName().equals(name)).findFirst().orElse(null);
    }

    public static File createTempFile(String fileName, String content) throws IOException {
        String dir = System.getProperty("java.io.tmpdir");
        File file = new File(dir + File.separator + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Created temporary file " + file.getAbsolutePath());
        return file;
    }

    /**
     * Remove all reactions from a message by a user or bot
     *
     * @param reaction The {@link MessageReaction} to search for the list of users to remove
     * @param users    The {@link List list} of {@link User users} to remove the reaction from
     */
    public static int getReactionCountExcludingList(MessageReaction reaction, List<User> users) {
        return (int) reaction.retrieveUsers()
            .stream()
            .filter(user -> !users.contains(user))
            .count();
    }

    public static Object jsonToObject(File file, Class<?> clazz) throws FileNotFoundException {
        BufferedReader br = new BufferedReader(new FileReader(file.getPath()));
        return NerdBotApp.GSON.fromJson(br, clazz);
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getFirstLine(Message message) {
        String firstLine = message.getContentRaw().split("\n")[0];

        if (firstLine.equals("")) {
            if (message.getEmbeds().get(0).getTitle() != null) {
                firstLine = message.getEmbeds().get(0).getTitle();
            } else {
                firstLine = "No Title Found";
            }
        }

        return (firstLine.length() > 30) ? firstLine.substring(0, 27) + "..." : firstLine;
    }

    @Nullable
    public static DiscordUser getOrAddUserToCache(Database database, String userId) {
        if (!database.isConnected()) {
            log.warn("Could not cache user because there is not a database connected!");
            return null;
        }

        DiscordUser discordUser = database.findDocument(database.getCollection("users", DiscordUser.class), "discordId", userId).first();
        if (discordUser == null) {
            discordUser = new DiscordUser(userId, new ArrayList<>(), new ArrayList<>(), new LastActivity());
        }

        if (NerdBotApp.USER_CACHE.getIfPresent(userId) == null) {
            NerdBotApp.USER_CACHE.put(userId, discordUser);
        }

        return NerdBotApp.USER_CACHE.getIfPresent(userId);
    }

    public static JsonObject makeHttpRequest(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format(url))).GET().build();
        String requestResponse;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        requestResponse = response.body();

        return NerdBotApp.GSON.fromJson(requestResponse, JsonObject.class);
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    public static String getIgn(User user) {
        // Stuffy: Gets display name from SBN guild
        Guild guild = NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId());
        log.info("Guild: " + guild);
        if (guild == null) {
            log.info("Guild is null, effective name: " + user.getEffectiveName());
            return user.getEffectiveName();
        }
        Member sbnMember = guild.retrieveMemberById(user.getId()).complete();
        log.info("SBN Member: " + sbnMember);
        if (sbnMember == null) {
            log.info("SBN Member is null, effective name: " + user.getEffectiveName());
            return user.getEffectiveName();
        }
        log.info("SBN Member nickname: " + sbnMember.getNickname());
        return sbnMember.getNickname();
    }

    /**
     * Initializes a font.
     *
     * @param path The path to the font in the resources' folder.
     *
     * @return The initialized font.
     */
    @Nullable
    public static Font initFont(String path, float size) {
        Font font;
        try (InputStream fontStream = ItemGenCommands.class.getResourceAsStream(path)) {
            if (fontStream == null) {
                log.error("Couldn't initialise font: " + path);
                return null;
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
            return null;
        }
        return font;
    }
  
    /**
     * Finds a matching value within a given set based on its name
     *
     * @param enumSet an array to search for the enum in
     * @param match   the value to find in the array
     *
     * @return returns the enum item or null if not found
     */
    @Nullable
    public static Enum<?> findValue(Enum<?>[] enumSet, String match) {
        for (Enum<?> enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name()))
                return enumItem;
        }

        return null;
    }

    public static JsonObject isJsonObject(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is actually a json object
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonObject()) {
            return null;
        }
        return foundItem.getAsJsonObject();
    }

    public static String isJsonString(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is a primitive type
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonPrimitive()) {
            return null;
        }
        return foundItem.getAsJsonPrimitive().getAsString();
    }

    public static JsonArray isJsonArray(JsonObject obj, String element) {
        // checking if the json object has the key
        if (!obj.has(element)) {
            return null;
        }
        // checking if the found element is an array
        JsonElement foundItem = obj.get(element);
        if (!foundItem.isJsonArray()) {
            return null;
        }
        return foundItem.getAsJsonArray();
    }
}
