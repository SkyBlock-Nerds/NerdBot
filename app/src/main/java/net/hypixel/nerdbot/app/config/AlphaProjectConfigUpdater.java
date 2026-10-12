package net.hypixel.nerdbot.app.config;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionTypeResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UtilityClass
@Slf4j
public class AlphaProjectConfigUpdater {

    /**
     * Sets the alpha and project forum ids to the guild's forums of each type. A forum already listed
     * keeps its type, so one added to the config by hand isn't dropped for not having a matching
     * name, and a forum that no longer exists is dropped.
     *
     * @return whether either list changed
     */
    public boolean updateForumIds(NerdBotConfig botConfig, List<ForumChannel> guildForumChannels) {
        AlphaProjectConfig alphaProjectConfig = botConfig.getAlphaProjectConfig();

        List<ForumChannel> forumChannels = new ArrayList<>(guildForumChannels);
        forumChannels.removeIf(forumChannel -> forumChannel.getId().equals(botConfig.getSuggestionConfig().getForumChannelId()));

        String[] alphaForumIds = forumIdsOfType(forumChannels, Suggestion.ChannelType.ALPHA, botConfig);
        String[] projectForumIds = forumIdsOfType(forumChannels, Suggestion.ChannelType.PROJECT, botConfig);
        boolean changed = !Arrays.equals(alphaForumIds, alphaProjectConfig.getAlphaForumIds())
            || !Arrays.equals(projectForumIds, alphaProjectConfig.getProjectForumIds());

        if (changed) {
            log.info("Alpha forums are now {} and project forums {}", Arrays.toString(alphaForumIds), Arrays.toString(projectForumIds));
            alphaProjectConfig.setAlphaForumIds(alphaForumIds);
            alphaProjectConfig.setProjectForumIds(projectForumIds);
        }

        return changed;
    }

    private String[] forumIdsOfType(List<ForumChannel> forumChannels, Suggestion.ChannelType type, NerdBotConfig botConfig) {
        return forumChannels.stream()
            .filter(forumChannel -> SuggestionTypeResolver.getForumSuggestionType(forumChannel, botConfig) == type)
            .map(ForumChannel::getId)
            .toArray(String[]::new);
    }
}