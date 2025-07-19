package net.hypixel.nerdbot.util;

import com.vdurmont.emoji.EmojiManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DiscordUtils {

    private DiscordUtils() {
    }

    @Nullable
    public static Guild getGuild(String guildId) {
        return NerdBotApp.getBot().getJDA().getGuildById(guildId);
    }

    @NotNull
    public static Guild getMainGuild() {
        return Objects.requireNonNull(NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()));
    }

    public static int getReactionCountExcludingList(MessageReaction reaction, List<User> users) {
        return (int) reaction.retrieveUsers()
            .stream()
            .filter(user -> !users.contains(user))
            .count();
    }

    public static Optional<Message> getFirstMessage(String threadId) {
        return getFirstMessage(NerdBotApp.getBot().getJDA().getThreadChannelById(threadId));
    }

    public static Optional<Message> getFirstMessage(ThreadChannel threadChannel) {
        if (threadChannel != null) {
            MessageHistory history = threadChannel.getHistoryFromBeginning(1).complete();
            return history.isEmpty() ? Optional.empty() : Optional.of(history.getRetrievedHistory().get(0));
        }

        return Optional.empty();
    }

    public static String getDisplayName(User user) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(user.getId());

        if (discordUser.isProfileAssigned()) {
            return discordUser.getMojangProfile().getUsername();
        } else {
            Guild guild = getMainGuild();
            Member sbnMember = guild.retrieveMemberById(user.getId()).complete();

            if (sbnMember == null || sbnMember.getNickname() == null) {
                return user.getEffectiveName();
            }

            return sbnMember.getNickname();
        }
    }

    public static Optional<Emoji> getEmoji(String emoji) {
        if (EmojiManager.isEmoji(emoji)) {
            return Optional.of(Emoji.fromUnicode(emoji));
        }

        return EmojiCache.getEmojiById(emoji);
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name) {
        return getTagByName(forumChannel, name, true);
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .filter(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)))
            .findFirst()
            .orElseThrow();
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name) {
        return hasTagByName(forumChannel, name, true);
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name) {
        return hasTagByName(threadChannel, name, true);
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name, boolean ignoreCase) {
        return threadChannel.getAppliedTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }
}