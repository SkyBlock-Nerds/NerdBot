package net.hypixel.nerdbot.discord.util;

import com.vdurmont.emoji.EmojiManager;
import net.hypixel.nerdbot.core.util.ArrayUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.core.BotEnvironment;
import net.hypixel.nerdbot.discord.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.discord.database.model.repository.DiscordUserRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class DiscordUtils {

    private DiscordUtils() {
    }

    @Nullable
    public static Guild getGuild(String guildId) {
        return DiscordBotEnvironment.getBot().getJDA().getGuildById(guildId);
    }

    @NotNull
    public static Guild getMainGuild() {
        return Objects.requireNonNull(DiscordBotEnvironment.getBot().getJDA().getGuildById(DiscordBotEnvironment.getBot().getConfig().getGuildId()));
    }

    public static int getReactionCountExcludingList(MessageReaction reaction, List<User> users) {
        return (int) reaction.retrieveUsers()
            .stream()
            .filter(user -> !users.contains(user))
            .count();
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

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel) {
        return getForumSuggestionType(threadChannel.getParentChannel().asForumChannel());
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel) {
        SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();
        AlphaProjectConfig alphaProjectConfig = DiscordBotEnvironment.getBot().getConfig().getAlphaProjectConfig();
        String parentChannelId = forumChannel.getId();

        if (ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(parentChannelId::equalsIgnoreCase)) {
            return Suggestion.ChannelType.ALPHA;
        } else if (ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(parentChannelId::equalsIgnoreCase)) {
            return Suggestion.ChannelType.PROJECT;
        } else if (parentChannelId.equals(suggestionConfig.getForumChannelId())) {
            return Suggestion.ChannelType.NORMAL;
        }

        Category parentCategory = forumChannel.getParentCategory();

        if (parentCategory != null) {
            return getChannelSuggestionTypeFromName(parentCategory.getName());
        }

        String[] projectChannelNames = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getProjectChannelNames();
        String channelName = forumChannel.getName().toLowerCase();

        if (channelName.contains("alpha") || Arrays.stream(projectChannelNames).anyMatch(channelName::contains)) {
            return getChannelSuggestionTypeFromName(forumChannel.getName());
        }

        return Suggestion.ChannelType.UNKNOWN;
    }

    public static Suggestion.ChannelType getChannelSuggestionType(StandardGuildChannel channel) {
        return getChannelSuggestionTypeFromName(channel.getName());
    }

    public static Suggestion.ChannelType getChannelSuggestionTypeFromName(String name) {
        if (name.toLowerCase().contains("alpha")) {
            return Suggestion.ChannelType.ALPHA;
        }

        String[] projectChannelNames = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getProjectChannelNames();
        if (Arrays.stream(projectChannelNames).anyMatch(name.toLowerCase()::contains)) {
            return Suggestion.ChannelType.PROJECT;
        }

        return Suggestion.ChannelType.NORMAL;
    }
}
