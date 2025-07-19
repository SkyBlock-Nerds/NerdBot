package net.hypixel.nerdbot.util;

import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;

import java.util.Arrays;

public class SuggestionUtils {


    private SuggestionUtils() {
    }

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel) {
        return getForumSuggestionType(threadChannel.getParentChannel().asForumChannel());
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel) {
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();
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

        String[] projectChannelNames = NerdBotApp.getBot().getConfig().getChannelConfig().getProjectChannelNames();
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
        
        String[] projectChannelNames = NerdBotApp.getBot().getConfig().getChannelConfig().getProjectChannelNames();
        if (Arrays.stream(projectChannelNames).anyMatch(name.toLowerCase()::contains)) {
            return Suggestion.ChannelType.PROJECT;
        }
        
        return Suggestion.ChannelType.NORMAL;
    }
}