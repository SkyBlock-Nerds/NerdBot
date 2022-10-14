package net.hypixel.nerdbot.util;

import net.dv8tion.jda.api.entities.*;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Util {

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final String DASHED_LINE = "----------------------------------------------------------------";

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

    @Nullable
    public static Role findRole(Member member, String id) {
        List<Role> roles = member.getRoles();
        return roles.stream()
                .filter(role -> role.getId().equals(id))
                .findFirst()
                .orElse(null);
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

    public static BotConfig loadConfig(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found!");
        }
        BufferedReader br = new BufferedReader(new FileReader(file.getPath()));
        return NerdBotApp.GSON.fromJson(br, BotConfig.class);
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
}
