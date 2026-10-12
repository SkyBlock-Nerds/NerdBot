package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.config.AlphaProjectConfigUpdater;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.suggestion.SuggestionTypeResolver;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.EmojiConfigUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Keeps suggestion posts, the suggestion cache and the alpha and project forum config in step with
 * the guild's forums.
 */
@Slf4j
public class SuggestionListener {

    static final String SUBMITTED_TAG = "Submitted";

    private final GuildScope guildScope;
    private final SuggestionCache suggestions;
    private final Provider<NerdBotConfig> config;
    private final ConfigStore configStore;

    @Inject
    public SuggestionListener(GuildScope guildScope, SuggestionCache suggestions, Provider<NerdBotConfig> config, ConfigStore configStore) {
        this.guildScope = guildScope;
        this.suggestions = suggestions;
        this.config = config;
        this.configStore = configStore;
    }

    @SubscribeEvent
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!guildScope.isMainGuild(event.getGuild())) {
            return;
        }

        if (event.getChannelType() == ChannelType.FORUM) {
            forumChanged(event.getChannel().asForumChannel());
        }

        suggestionThread(event.getChannel()).ifPresent(thread -> {
            suggestions.requestUpdate(thread);
            applyAutoTag(thread);
        });
    }

    @SubscribeEvent
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        if (guildScope.isMainGuild(event.getGuild()) && event.getChannelType() == ChannelType.FORUM) {
            forumChanged(event.getChannel().asForumChannel());
        }
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!guildScope.isMainGuild(event.getGuild())) {
            return;
        }

        if (event.getChannelType() == ChannelType.FORUM) {
            forumDeleted(event.getChannel().asForumChannel());
        }

        suggestionThread(event.getChannel()).ifPresent(thread -> suggestions.remove(thread.getId()));
    }

    @SubscribeEvent
    public void onChannelUpdateAppliedTags(ChannelUpdateAppliedTagsEvent event) {
        if (!guildScope.isMainGuild(event.getGuild()) || !(event.getChannel() instanceof ThreadChannel thread)
            || thread.getParentChannel().getType() != ChannelType.FORUM) {
            return;
        }

        List<Long> oldTagIds = event.getOldValue();
        thread.getAppliedTags().stream()
            .filter(tag -> !oldTagIds.contains(tag.getIdLong()))
            .forEach(tag -> swapAutoTag(thread, tag.getName()));
    }

    @SubscribeEvent
    public void onReactionAdd(MessageReactionAddEvent event) {
        voteChanged(event, event.getReaction());
    }

    @SubscribeEvent
    public void onReactionRemove(MessageReactionRemoveEvent event) {
        voteChanged(event, event.getReaction());
    }

    private void voteChanged(GenericMessageReactionEvent event, MessageReaction reaction) {
        if (!event.isFromGuild() || !guildScope.isMainGuild(event.getGuild())
            || !event.getMessageId().equals(event.getChannel().getId())
            || !EmojiConfigUtils.isVote(config.get().getEmojiConfig(), reaction)) {
            return;
        }

        suggestionThread(event.getChannel()).ifPresent(suggestions::requestUpdate);
    }

    Optional<ThreadChannel> suggestionThread(Channel channel) {
        if (!(channel instanceof ThreadChannel thread) || channel.getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            return Optional.empty();
        }

        return SuggestionTypeResolver.getConfiguredForumType(thread.getParentChannel().getId(), config.get()).map(type -> thread);
    }

    void forumChanged(ForumChannel forum) {
        NerdBotConfig current = config.get();
        boolean changed = AlphaProjectConfigUpdater.updateForumIds(current, forum.getGuild().getForumChannels());
        Suggestion.ChannelType type = SuggestionTypeResolver.getForumSuggestionType(forum, current);

        if (type == Suggestion.ChannelType.ALPHA || type == Suggestion.ChannelType.PROJECT) {
            String reviewedTag = current.getSuggestionConfig().getReviewedTag();
            if (current.getAlphaProjectConfig().isAutoCreateTags()) {
                addMissingTags(forum, List.of(SUBMITTED_TAG, reviewedTag));
            }

            if (current.getChannelConfig().addForumAutoTagConfigIfAbsent(forum.getId(), SUBMITTED_TAG, reviewedTag)) {
                log.info("Auto-tagging {} forum '{}' ({}): {} -> {}", type.getName(), forum.getName(), forum.getId(), SUBMITTED_TAG, reviewedTag);
                changed = true;
            }
        }

        if (changed) {
            save(current, "forum '" + forum.getName() + "' changed");
        }
    }

    void forumDeleted(ForumChannel forum) {
        NerdBotConfig current = config.get();
        boolean changed = AlphaProjectConfigUpdater.updateForumIds(current, forum.getGuild().getForumChannels());

        if (current.getChannelConfig().removeForumAutoTagConfig(forum.getId())) {
            log.info("Removed auto-tagging for deleted forum '{}' ({})", forum.getName(), forum.getId());
            changed = true;
        }

        if (changed) {
            save(current, "forum '" + forum.getName() + "' was deleted");
        }
    }

    private void addMissingTags(ForumChannel forum, List<String> tagNames) {
        List<BaseForumTag> tags = new ArrayList<>(forum.getAvailableTags());
        List<String> missing = tagNames.stream()
            .distinct()
            .filter(name -> !DiscordUtils.hasTagByName(forum, name))
            .toList();
        if (missing.isEmpty()) {
            return;
        }

        missing.forEach(name -> tags.add(new ForumTagData(name).setModerated(true)));
        forum.getManager().setAvailableTags(tags).queue(
            success -> log.info("Created tags {} in forum '{}' ({})", missing, forum.getName(), forum.getId()),
            error -> log.error("Failed to create tags {} in forum '{}' ({})", missing, forum.getName(), forum.getId(), error)
        );
    }

    private void save(NerdBotConfig current, String reason) {
        try {
            configStore.save(current);
            log.info("Saved the config after {}", reason);
        } catch (IOException exception) {
            log.error("Couldn't save the config after {}; the change only lasts until the bot restarts", reason, exception);
        }
    }

    private Optional<ForumAutoTag> autoTag(ForumChannel forum) {
        return Optional.ofNullable(config.get().getChannelConfig().getForumAutoTagConfig(forum.getId()));
    }

    private void applyAutoTag(ThreadChannel thread) {
        ForumChannel forum = thread.getParentChannel().asForumChannel();
        autoTag(forum).ifPresent(autoTag -> {
            Optional<ForumTag> defaultTag = DiscordUtils.findTagByName(forum, autoTag.getDefaultTagName());
            if (defaultTag.isEmpty()) {
                log.warn("Auto-tag '{}' not found in forum '{}' ({})", autoTag.getDefaultTagName(), forum.getName(), forum.getId());
                return;
            }

            if (thread.getAppliedTags().contains(defaultTag.get())) {
                return;
            }

            List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
            tags.add(defaultTag.get());
            thread.getManager().setAppliedTags(tags).queue(
                success -> log.info("Auto-applied tag '{}' to thread '{}' ({})", autoTag.getDefaultTagName(), thread.getName(), thread.getId()),
                error -> log.error("Failed to auto-apply tag '{}' to thread '{}' ({})", autoTag.getDefaultTagName(), thread.getName(), thread.getId(), error)
            );
        });
    }

    /** Takes the default tag off once the review tag goes on. */
    private void swapAutoTag(ThreadChannel thread, String addedTagName) {
        ForumChannel forum = thread.getParentChannel().asForumChannel();
        autoTag(forum)
            .filter(autoTag -> autoTag.getReviewTagName().equalsIgnoreCase(addedTagName))
            .ifPresent(autoTag -> DiscordUtils.findTagByName(forum, autoTag.getDefaultTagName())
                .filter(thread.getAppliedTags()::contains)
                .ifPresent(defaultTag -> {
                    List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                    tags.remove(defaultTag);
                    thread.getManager().setAppliedTags(tags).queue(
                        success -> log.info("Removed auto-tag '{}' from thread '{}' ({}) after '{}' was added",
                            autoTag.getDefaultTagName(), thread.getName(), thread.getId(), addedTagName),
                        error -> log.error("Failed to remove auto-tag '{}' from thread '{}' ({})",
                            autoTag.getDefaultTagName(), thread.getName(), thread.getId(), error)
                    );
                }));
    }
}
