package net.hypixel.nerdbot.util;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.config.BotConfig;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Util {

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

    public static boolean isMod(String userId, String guildId) {
        Guild guild = getGuild(guildId);
        if (guild == null) return false;

        Member member = guild.getMemberById(userId);
        if (member == null) return false;

        return member.hasPermission(Permission.BAN_MEMBERS);
    }

    @Nullable
    public static Role findRole(Member member, String id) {
        List<Role> roles = member.getRoles();
        return roles.stream()
                .filter(role -> role.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public static Role findRole(User user, String guildId, String roleId) {
        Guild guild = getGuild(guildId);
        if (guild == null) return null;

        Member member = guild.getMember(user);
        if (member == null) return null;

        return findRole(member, roleId);
    }

    public static BotConfig loadConfig(File file) throws FileNotFoundException {
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found!");
        }
        BufferedReader br = new BufferedReader(new FileReader(file.getPath()));
        return NerdBotApp.GSON.fromJson(br, BotConfig.class);
    }

}
