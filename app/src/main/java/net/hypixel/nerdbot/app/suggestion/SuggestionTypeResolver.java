package net.hypixel.nerdbot.app.suggestion;

import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.config.AlphaProjectConfig;
import net.hypixel.nerdbot.app.config.SuggestionConfig;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;

import java.util.Arrays;

/**
 * Resolves which kind of suggestion channel (normal / alpha / project) a thread, forum, or channel
 * belongs to, using the configured suggestion and alpha/project forum settings.
 */
public final class SuggestionTypeResolver {

    private SuggestionTypeResolver() {
    }

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel) {
        return getForumSuggestionType(threadChannel.getParentChannel().asForumChannel());
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel) {
        SuggestionConfig suggestionConfig = SkyBlockNerdsBot.config().getSuggestionConfig();
        AlphaProjectConfig alphaProjectConfig = SkyBlockNerdsBot.config().getAlphaProjectConfig();
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

        String[] projectChannelNames = SkyBlockNerdsBot.config().getChannelConfig().getProjectChannelNames();
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

        String[] projectChannelNames = SkyBlockNerdsBot.config().getChannelConfig().getProjectChannelNames();
        if (Arrays.stream(projectChannelNames).anyMatch(name.toLowerCase()::contains)) {
            return Suggestion.ChannelType.PROJECT;
        }

        return Suggestion.ChannelType.NORMAL;
    }
}
