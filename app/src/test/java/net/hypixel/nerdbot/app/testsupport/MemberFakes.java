package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Guild members for listener tests. A member's roles can be changed after it's made. */
public final class MemberFakes {

    public static final String MAIN_GUILD_ID = "100";

    private MemberFakes() {
    }

    public static Guild guild(String id) {
        return Fakes.of(Guild.class, Map.of("getId", id));
    }

    public static Role role(String id) {
        return Fakes.of(Role.class, Map.of("getId", id, "getName", "role-" + id));
    }

    public static User user(String id, boolean bot) {
        return Fakes.of(User.class, Map.of("getId", id, "getName", "user-" + id, "isBot", bot, "getAsMention", "<@" + id + ">"));
    }

    public static Member member(String id, Role... roles) {
        return member(id, false, new ArrayList<>(List.of(roles)));
    }

    public static Member bot(String id) {
        return member(id, true, new ArrayList<>());
    }

    /** A member whose roles are whatever {@code roles} holds when they're asked for. */
    public static Member member(String id, boolean bot, List<Role> roles) {
        return Fakes.of(Member.class, Map.of(
            "getId", id,
            "getIdLong", Long.parseLong(id),
            "getUser", user(id, bot),
            "getEffectiveName", "member-" + id,
            "getAsMention", "<@" + id + ">",
            "getGuild", guild(MAIN_GUILD_ID),
            "getRoles", (CallRecorder.Answer) args -> List.copyOf(roles)));
    }
}
