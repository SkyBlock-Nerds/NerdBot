package net.hypixel.nerdbot.discord.util;

import com.vdurmont.emoji.EmojiManager;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class DiscordUtils {

    @NotNull
    public static Guild getMainGuild() {
        return Objects.requireNonNull(DiscordBotEnvironment.getBot().getJDA().getGuildById(DiscordBotEnvironment.getBot().getConfig().getGuildId()));
    }

    public static Optional<Message> getFirstMessage(String threadId) {
        return getFirstMessage(DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(threadId));
    }

    public static Optional<Message> getFirstMessage(ThreadChannel threadChannel) {
        if (threadChannel != null) {
            MessageHistory history = threadChannel.getHistoryFromBeginning(1).complete();
            return history.isEmpty() ? Optional.empty() : Optional.of(history.getRetrievedHistory().get(0));
        }

        return Optional.empty();
    }

    public static String getDisplayName(User user) {
        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(user.getId()).orElse(null);

        if (discordUser != null && discordUser.isProfileAssigned()) {
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

    /**
     * @throws NoSuchElementException when the forum has no such tag; use {@link #findTagByName} when that's expected
     */
    public static ForumTag getTagByName(ForumChannel forumChannel, String name) {
        return getTagByName(forumChannel, name, true);
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return findTagByName(forumChannel, name, ignoreCase)
            .orElseThrow(() -> new NoSuchElementException("Forum " + forumChannel.getName() + " has no tag named " + name));
    }

    public static Optional<ForumTag> findTagByName(ForumChannel forumChannel, String name) {
        return findTagByName(forumChannel, name, true);
    }

    public static Optional<ForumTag> findTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags().stream()
            .filter(forumTag -> tagNameMatches(forumTag, name, ignoreCase))
            .findFirst();
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name) {
        return hasTagByName(forumChannel, name, true);
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return findTagByName(forumChannel, name, ignoreCase).isPresent();
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name) {
        return hasTagByName(threadChannel, name, true);
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name, boolean ignoreCase) {
        return threadChannel.getAppliedTags().stream().anyMatch(forumTag -> tagNameMatches(forumTag, name, ignoreCase));
    }

    private static boolean tagNameMatches(ForumTag forumTag, String name, boolean ignoreCase) {
        return ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name);
    }
}
