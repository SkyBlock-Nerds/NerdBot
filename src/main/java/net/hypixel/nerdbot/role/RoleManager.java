package net.hypixel.nerdbot.role;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RoleManager {

    private RoleManager() {
    }

    public static Optional<PingableRole> getPingableRoleByName(String name) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .filter(pingableRole -> pingableRole.name().equalsIgnoreCase(name))
            .findFirst();
    }

    public static Optional<PingableRole> getPingableRoleById(String id) {
        return Arrays.stream(NerdBotApp.getBot().getConfig().getRoleConfig().getPingableRoles())
            .filter(pingableRole -> pingableRole.roleId().equalsIgnoreCase(id))
            .findFirst();
    }

    public static String formatPingableRoleAsMention(@NotNull PingableRole pingableRole) {
        return "<@&" + pingableRole.roleId() + ">";
    }

    public static boolean hasRole(Member member, String name) {
        return member.getRoles().stream().anyMatch(role -> role.getName().equalsIgnoreCase(name));
    }

    public static boolean hasAnyRole(Member member, String... names) {
        List<Role> roles = member.getRoles();
        List<String> nameList = Arrays.asList(names);

        if (names.length == 0) {
            return false;
        } else {
            return roles.stream().anyMatch(role -> nameList.stream().anyMatch(name -> role.getName().equalsIgnoreCase(name)));
        }
    }

    public static boolean hasHigherOrEqualRole(Member member, Role role) {
        return member.getRoles().stream().anyMatch(memberRole -> memberRole.getPosition() >= role.getPosition());
    }

    public static Optional<Role> getRole(String name) {
        return Util.getMainGuild().getRoles().stream()
            .filter(role -> role.getName().equals(name))
            .findFirst();
    }

    public static Role getHighestRole(Member member) {
        return member.getRoles().get(0);
    }

    public static Optional<Role> getRoleById(String id) {
        return Util.getMainGuild().getRoles().stream()
            .filter(role -> role.getId().equals(id))
            .findFirst();
    }
}
