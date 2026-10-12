package net.hypixel.nerdbot.app.suggestion;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.AlphaProjectConfig;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * Resolves which kind of suggestion channel (normal / alpha / project) a thread, forum, or channel
 * belongs to, using the configured suggestion and alpha/project forum settings. The overloads
 * without a config read the running bot's, for code that hasn't moved to injection yet.
 */
public final class SuggestionTypeResolver {

    private SuggestionTypeResolver() {
    }

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel) {
        return getThreadSuggestionType(threadChannel, SkyBlockNerdsBot.config());
    }

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel, NerdBotConfig config) {
        IThreadContainerUnion parent = threadChannel.getParentChannel();
        return parent.getType() == ChannelType.FORUM
            ? getForumSuggestionType(parent.asForumChannel(), config)
            : getChannelSuggestionTypeFromName(parent.getName(), config);
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel) {
        return getForumSuggestionType(forumChannel, SkyBlockNerdsBot.config());
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel, NerdBotConfig config) {
        Optional<Suggestion.ChannelType> configured = getConfiguredForumType(forumChannel.getId(), config);
        if (configured.isPresent()) {
            return configured.get();
        }

        Category parentCategory = forumChannel.getParentCategory();

        if (parentCategory != null) {
            return getChannelSuggestionTypeFromName(parentCategory.getName(), config);
        }

        String[] projectChannelNames = config.getChannelConfig().getProjectChannelNames();
        String channelName = forumChannel.getName().toLowerCase();

        if (channelName.contains("alpha") || Arrays.stream(projectChannelNames).anyMatch(channelName::contains)) {
            return getChannelSuggestionTypeFromName(forumChannel.getName(), config);
        }

        return Suggestion.ChannelType.UNKNOWN;
    }

    /** The type of a forum named in the config, without the guessing from names the other lookups fall back to. */
    public static Optional<Suggestion.ChannelType> getConfiguredForumType(String forumId, NerdBotConfig config) {
        AlphaProjectConfig alphaProjectConfig = config.getAlphaProjectConfig();

        if (ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(forumId::equals)) {
            return Optional.of(Suggestion.ChannelType.ALPHA);
        } else if (ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(forumId::equals)) {
            return Optional.of(Suggestion.ChannelType.PROJECT);
        } else if (forumId.equals(config.getSuggestionConfig().getForumChannelId())) {
            return Optional.of(Suggestion.ChannelType.NORMAL);
        }

        return Optional.empty();
    }

    public static Suggestion.ChannelType getChannelSuggestionType(StandardGuildChannel channel) {
        return getChannelSuggestionTypeFromName(channel.getName(), SkyBlockNerdsBot.config());
    }

    public static Suggestion.ChannelType getChannelSuggestionTypeFromName(String name) {
        return getChannelSuggestionTypeFromName(name, SkyBlockNerdsBot.config());
    }

    public static Suggestion.ChannelType getChannelSuggestionTypeFromName(String name, NerdBotConfig config) {
        if (name.toLowerCase().contains("alpha")) {
            return Suggestion.ChannelType.ALPHA;
        }

        String[] projectChannelNames = config.getChannelConfig().getProjectChannelNames();
        if (Arrays.stream(projectChannelNames).anyMatch(name.toLowerCase()::contains)) {
            return Suggestion.ChannelType.PROJECT;
        }

        return Suggestion.ChannelType.NORMAL;
    }
}
