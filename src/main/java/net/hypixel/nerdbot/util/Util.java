package net.hypixel.nerdbot.util;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.user.DiscordUser;
import net.hypixel.nerdbot.api.database.user.LastActivity;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Log4j2
public class Util {

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

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

    public static DiscordUser getOrAddUserToCache(Database database, String userId) {
        DiscordUser discordUser = database.findDocument(database.getCollection("users", DiscordUser.class), "discordId", userId).first();
        if (discordUser == null) {
            discordUser = new DiscordUser(userId, new ArrayList<>(), new ArrayList<>(), new LastActivity());
        }
        if (NerdBotApp.USER_CACHE.getIfPresent(userId) == null) {
            NerdBotApp.USER_CACHE.put(userId, discordUser);
        }
        return NerdBotApp.USER_CACHE.getIfPresent(userId);
    }
}
