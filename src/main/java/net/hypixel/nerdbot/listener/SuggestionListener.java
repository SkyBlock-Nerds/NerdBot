package net.hypixel.nerdbot.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.managers.channel.concrete.ForumChannelManager;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.util.ArrayUtils;
import net.hypixel.nerdbot.util.DiscordUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SuggestionListener {

    @SubscribeEvent
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        updateConfigForumIds(event);

        if (isInSuggestionChannel(event)) {
            ThreadChannel thread = event.getChannel().asThreadChannel();
            NerdBotApp.getBot().getSuggestionCache().addSuggestion(thread);

            // Apply auto-tag if configured for this forum
            applyAutoTag(thread);
        }
    }

    @SubscribeEvent
    public void onChannelUpdate(@NotNull ChannelUpdateNameEvent event) {
        updateConfigForumIds(event);
    }

    @SubscribeEvent
    public void onChannelUpdateAppliedTags(@NotNull ChannelUpdateAppliedTagsEvent event) {
        if (!(event.getChannel() instanceof ThreadChannel thread) || !(thread.getParentChannel() instanceof ForumChannel forum)) {
            return;
        }

        List<Long> oldTagIds = event.getOldValue();
        List<ForumTag> currentTags = thread.getAppliedTags();

        for (ForumTag currentTag : currentTags) {
            if (!oldTagIds.contains(currentTag.getIdLong())) {
                swapAutoTag(thread, currentTag.getName());
            }
        }
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.FORUM) {
            Suggestion.ChannelType channelType = DiscordUtils.getForumSuggestionType(event.getChannel().asForumChannel());

            if (channelType == Suggestion.ChannelType.ALPHA || channelType == Suggestion.ChannelType.PROJECT) {
                BotConfig botConfig = NerdBotApp.getBot().getConfig();
                botConfig.getAlphaProjectConfig().updateForumIds(botConfig, channelType == Suggestion.ChannelType.ALPHA, channelType == Suggestion.ChannelType.PROJECT);

                boolean removed = botConfig.getChannelConfig().removeForumAutoTagConfig(event.getChannel().getId());
                if (removed) {
                    NerdBotApp.getBot().writeConfig(botConfig);
                    log.info("Removed forum auto-tagging config for deleted forum '{}' (ID: {})", event.getChannel().getName(), event.getChannel().getId());
                }

                log.info("Removed {} suggestion forum from bot config: {} (ID: {})", channelType.getName(), event.getChannel().getName(), event.getChannel().getId());
            }
        }

        if (isInSuggestionChannel(event)) {
            NerdBotApp.getBot().getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
        }
    }

    private void updateConfigForumIds(GenericChannelEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.FORUM) {
            ForumChannel forumChannel = event.getChannel().asForumChannel();
            Suggestion.ChannelType channelType = DiscordUtils.getForumSuggestionType(forumChannel);

            log.info("Forum channel event detected: '{}' (ID: {}), detected type: {}, parent category: {}",
                forumChannel.getName(),
                forumChannel.getId(),
                channelType.getName(),
                forumChannel.getParentCategory() != null ? forumChannel.getParentCategory().getName() : "none");

            if (channelType == Suggestion.ChannelType.ALPHA || channelType == Suggestion.ChannelType.PROJECT) {
                BotConfig botConfig = NerdBotApp.getBot().getConfig();
                AlphaProjectConfig alphaProjectConfig = botConfig.getAlphaProjectConfig();
                alphaProjectConfig.updateForumIds(botConfig, channelType == Suggestion.ChannelType.ALPHA, channelType == Suggestion.ChannelType.PROJECT);

                if (alphaProjectConfig.isAutoCreateTags()) {
                    ForumChannelManager forumChannelManager = forumChannel.getManager();
                    List<BaseForumTag> currentTags = new ArrayList<>(forumChannelManager.getChannel().getAvailableTags());

                    String submittedTag = "Submitted";
                    String reviewedTag = botConfig.getSuggestionConfig().getReviewedTag();

                    if (currentTags.stream().noneMatch(baseForumTag -> baseForumTag.getName().equalsIgnoreCase(submittedTag))) {
                        currentTags.add(new ForumTagData(submittedTag).setModerated(true));
                        log.info("Auto-created '{}' tag for forum '{}' (ID: {})", submittedTag, forumChannel.getName(), forumChannel.getId());
                    }

                    if (currentTags.stream().noneMatch(baseForumTag -> baseForumTag.getName().equalsIgnoreCase(reviewedTag))) {
                        currentTags.add(new ForumTagData(reviewedTag).setModerated(true));
                        log.info("Auto-created '{}' tag for forum '{}' (ID: {})", reviewedTag, forumChannel.getName(), forumChannel.getId());
                    }

                    forumChannelManager.setAvailableTags(currentTags).queue();
                }

                boolean added = botConfig.getChannelConfig().addOrUpdateForumAutoTagConfig(
                    forumChannel.getId(),
                    "Submitted",
                    botConfig.getSuggestionConfig().getReviewedTag()
                );

                if (added) {
                    NerdBotApp.getBot().writeConfig(botConfig);
                    log.info("Auto-configured forum auto-tagging for '{}' (ID: {}): Submitted -> {}",
                        forumChannel.getName(), forumChannel.getId(), botConfig.getSuggestionConfig().getReviewedTag());
                }

                log.info("New {} suggestion forum created and added to bot config: {} (ID: {})", channelType.getName(), event.getChannel().getName(), event.getChannel().getId());
            }
        }
    }

    private boolean isInSuggestionChannel(GenericChannelEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();
            String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

            return forumChannelId.equals(suggestionConfig.getForumChannelId())
                || ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(forumChannelId::equals)
                || ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(forumChannelId::equals);
        }

        return false;
    }

    /**
     * Applies the configured auto-tag to a newly created forum post
     */
    private void applyAutoTag(ThreadChannel thread) {
        ForumChannel forumChannel = thread.getParentChannel().asForumChannel();
        ForumAutoTag autoTagConfig = NerdBotApp.getBot().getConfig().getChannelConfig().getForumAutoTagConfig(forumChannel.getId());

        if (autoTagConfig == null) {
            return;
        }

        if (!DiscordUtils.hasTagByName(forumChannel, autoTagConfig.getDefaultTagName())) {
            log.warn("Auto-tag '{}' not found in forum '{}' (ID: {})", autoTagConfig.getDefaultTagName(), forumChannel.getName(), forumChannel.getId());
            return;
        }

        ForumTag defaultTag = DiscordUtils.getTagByName(forumChannel, autoTagConfig.getDefaultTagName());

        if (thread.getAppliedTags().contains(defaultTag)) {
            return;
        }

        List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
        tags.add(defaultTag);

        thread.getManager().setAppliedTags(tags).queue(
            success -> log.info("Auto-applied tag '{}' to thread '{}' (ID: {})", autoTagConfig.getDefaultTagName(), thread.getName(), thread.getId()),
            error -> log.error("Failed to auto-apply tag '{}' to thread '{}' (ID: {})", autoTagConfig.getDefaultTagName(), thread.getName(), thread.getId(), error)
        );
    }

    /**
     * Removes the default auto-tag when a review tag is added to a thread
     */
    private void swapAutoTag(ThreadChannel thread, String reviewTagName) {
        ForumChannel forumChannel = thread.getParentChannel().asForumChannel();
        ForumAutoTag autoTagConfig = NerdBotApp.getBot().getConfig().getChannelConfig().getForumAutoTagConfig(forumChannel.getId());

        if (autoTagConfig == null) {
            return;
        }

        if (!autoTagConfig.getReviewTagName().equalsIgnoreCase(reviewTagName)) {
            return;
        }

        if (!DiscordUtils.hasTagByName(forumChannel, autoTagConfig.getDefaultTagName())) {
            return;
        }

        ForumTag defaultTag = DiscordUtils.getTagByName(forumChannel, autoTagConfig.getDefaultTagName());

        if (!thread.getAppliedTags().contains(defaultTag)) {
            return;
        }

        List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
        tags.remove(defaultTag);

        thread.getManager().setAppliedTags(tags).queue(
            success -> log.info("Removed auto-tag '{}' from thread '{}' (ID: {}) after '{}' tag was added", autoTagConfig.getDefaultTagName(), thread.getName(), thread.getId(), reviewTagName),
            error -> log.error("Failed to remove auto-tag '{}' from thread '{}' (ID: {})", autoTagConfig.getDefaultTagName(), thread.getName(), thread.getId(), error)
        );
    }
}
