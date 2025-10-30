package net.hypixel.nerdbot.discord.config;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.config.NerdBotConfig;
import net.hypixel.nerdbot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.util.DiscordUtils;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
@Slf4j
public class AlphaProjectConfigUpdater {

    public void updateForumIds(NerdBotConfig botConfig, boolean updateAlpha, boolean updateProject, List<ForumChannel> guildForumChannels) {
        AlphaProjectConfig alphaProjectConfig = botConfig.getAlphaProjectConfig();

        List<ForumChannel> forumChannels = new ArrayList<>(guildForumChannels);
        forumChannels.removeIf(forumChannel -> forumChannel.getId().equals(botConfig.getSuggestionConfig().getForumChannelId()));

        if (updateAlpha) {
            log.info("Updating Alpha Forum IDs");
            alphaProjectConfig.setAlphaForumIds(forumChannels.stream()
                .filter(forumChannel -> DiscordUtils.getForumSuggestionType(forumChannel) == Suggestion.ChannelType.ALPHA)
                .map(ForumChannel::getId)
                .toArray(String[]::new));
        }

        if (updateProject) {
            log.info("Updating Project Forum IDs");
            alphaProjectConfig.setProjectForumIds(forumChannels.stream()
                .filter(forumChannel -> DiscordUtils.getForumSuggestionType(forumChannel) == Suggestion.ChannelType.PROJECT)
                .map(ForumChannel::getId)
                .toArray(String[]::new));
        }
    }
}