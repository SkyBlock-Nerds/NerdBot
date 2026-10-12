package net.hypixel.nerdbot.app.testsupport;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.hypixel.nerdbot.discord.testsupport.Fakes;

import java.util.HashMap;
import java.util.Map;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.guild;

/** Channels and the threads in them, for listeners that look at where a message was sent. */
public final class ThreadFakes {

    private static final Guild GUILD = guild(MAIN_GUILD_ID);

    private ThreadFakes() {
    }

    public static TextChannel textChannel(String id, String name) {
        return Fakes.of(TextChannel.class, Map.of("getId", id, "getName", name, "getType", ChannelType.TEXT, "getGuild", GUILD));
    }

    public static AudioChannel voiceChannel(String id, String name) {
        return Fakes.of(AudioChannel.class, Map.of("getId", id, "getName", name, "getType", ChannelType.VOICE, "getGuild", GUILD));
    }

    public static ForumChannel forumChannel(String id, String name) {
        return Fakes.of(ForumChannel.class, Map.of("getId", id, "getName", name, "getType", ChannelType.FORUM, "getGuild", GUILD));
    }

    /** A forum as a thread's parent, which JDA hands out as a union. */
    public static IThreadContainerUnion forumParent(String id, String name) {
        ForumChannel forum = forumChannel(id, name);
        return Fakes.of(IThreadContainerUnion.class, Map.of(
            "getId", id, "getName", name, "getType", ChannelType.FORUM, "getGuild", GUILD, "asForumChannel", forum));
    }

    public static IThreadContainerUnion textParent(String id, String name) {
        return Fakes.of(IThreadContainerUnion.class, Map.of("getId", id, "getName", name, "getType", ChannelType.TEXT, "getGuild", GUILD));
    }

    public static ThreadChannel thread(String id, IThreadContainerUnion parent) {
        return thread(id, parent, Map.of());
    }

    public static ThreadChannel thread(String id, IThreadContainerUnion parent, Map<String, Object> extraAnswers) {
        Map<String, Object> answers = new HashMap<>(Map.of(
            "getId", id,
            "getIdLong", Long.parseLong(id),
            "getName", "thread-" + id,
            "getType", ChannelType.GUILD_PUBLIC_THREAD,
            "getParentChannel", parent,
            "getGuild", GUILD));
        answers.putAll(extraAnswers);
        return Fakes.of(ThreadChannel.class, answers);
    }
}
