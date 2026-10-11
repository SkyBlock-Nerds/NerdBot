package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A guild whose channels hide or show themselves through permission overrides, for code that
 * works out who can see a channel. Channels and roles added here are what the guild returns.
 */
public final class ChannelFakes {

    private final Role everyone = role("0", "@everyone");
    private final Map<String, Role> roles = new HashMap<>(Map.of("0", everyone));
    private final List<GuildChannel> channels = new ArrayList<>();
    private final Guild guild = Fakes.of(Guild.class, Map.of(
        "getPublicRole", everyone,
        "getRoleById", (CallRecorder.Answer) args -> roles.get(String.valueOf(args[0])),
        "getChannels", (CallRecorder.Answer) args -> List.copyOf(channels)));

    public Guild guild() {
        return guild;
    }

    public Role addRole(String id, String name) {
        Role role = role(id, name);
        roles.put(id, role);
        return role;
    }

    /** A text channel anyone can see. */
    public TextChannel publicChannel(String id) {
        return textChannel(id, ChannelType.TEXT, false, List.of(), null);
    }

    /** A text channel @everyone can't see, which {@code allowed} can. */
    public TextChannel hiddenChannel(String id, Role... allowed) {
        return textChannel(id, ChannelType.TEXT, true, List.of(allowed), null);
    }

    public TextChannel hiddenChannelIn(String id, Category parent, Role... allowed) {
        return textChannel(id, ChannelType.TEXT, true, List.of(allowed), parent);
    }

    public TextChannel textChannel(String id, ChannelType type, boolean hidden, List<Role> allowed, @Nullable Category parent) {
        TextChannel[] self = new TextChannel[1];
        Map<String, Object> answers = new HashMap<>(overrideAnswers(hidden, allowed));
        answers.put("getId", id);
        answers.put("getName", "channel-" + id);
        answers.put("getAsMention", "<#" + id + ">");
        answers.put("getType", type);
        answers.put("getGuild", guild);
        answers.put("getPermissionContainer", (CallRecorder.Answer) args -> self[0]);
        answers.put("getParentCategory", (CallRecorder.Answer) args -> parent);
        self[0] = Fakes.of(TextChannel.class, answers);
        channels.add(self[0]);
        return self[0];
    }

    public Category hiddenCategory(String id, Role... allowed) {
        Map<String, Object> answers = new HashMap<>(overrideAnswers(true, List.of(allowed)));
        answers.put("getId", id);
        answers.put("getType", ChannelType.CATEGORY);
        answers.put("getGuild", guild);
        return Fakes.of(Category.class, answers);
    }

    public void remove(GuildChannel channel) {
        channels.remove(channel);
    }

    private Map<String, Object> overrideAnswers(boolean hidden, List<Role> allowed) {
        PermissionOverride hiddenFromEveryone = hidden ? override(everyone, EnumSet.noneOf(Permission.class), EnumSet.of(Permission.VIEW_CHANNEL)) : null;
        List<PermissionOverride> overrides = new ArrayList<>();
        if (hiddenFromEveryone != null) {
            overrides.add(hiddenFromEveryone);
        }
        allowed.forEach(role -> overrides.add(override(role, EnumSet.of(Permission.VIEW_CHANNEL), EnumSet.noneOf(Permission.class))));

        return Map.of(
            "getPermissionOverride", (CallRecorder.Answer) args -> args[0] == everyone ? hiddenFromEveryone : null,
            "getPermissionOverrides", List.copyOf(overrides));
    }

    private static PermissionOverride override(Role role, EnumSet<Permission> allowed, EnumSet<Permission> denied) {
        return Fakes.of(PermissionOverride.class, Map.of("isRoleOverride", true, "getRole", role, "getAllowed", allowed, "getDenied", denied));
    }

    private static Role role(String id, String name) {
        return Fakes.of(Role.class, Map.of("getId", id, "getName", name));
    }
}
