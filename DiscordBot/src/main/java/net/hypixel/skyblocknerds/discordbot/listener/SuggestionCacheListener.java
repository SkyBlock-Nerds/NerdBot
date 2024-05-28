package net.hypixel.skyblocknerds.discordbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateAppliedTagsEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.cache.suggestion.Suggestion;
import net.hypixel.skyblocknerds.api.cache.suggestion.SuggestionCache;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.configuration.GuildConfiguration;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
public class SuggestionCacheListener extends ListenerAdapter {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();

        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            ThreadChannel threadChannel = event.getGuildChannel().asThreadChannel();
            insertSuggestion(threadChannel, message);
        }
    }

    @SubscribeEvent
    public void onMessageUpdate(MessageUpdateEvent event) {
        Message message = event.getMessage();

        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            ThreadChannel threadChannel = event.getGuildChannel().asThreadChannel();
            insertSuggestion(threadChannel, message);
        }
    }

    @SubscribeEvent
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        Message message = event.retrieveMessage().complete();

        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            ThreadChannel threadChannel = event.getGuildChannel().asThreadChannel();
            insertSuggestion(threadChannel, message);
        }
    }

    @SubscribeEvent
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        Message message = event.retrieveMessage().complete();

        if (event.isFromType(ChannelType.GUILD_PUBLIC_THREAD)) {
            ThreadChannel threadChannel = event.getGuildChannel().asThreadChannel();
            insertSuggestion(threadChannel, message);
        }
    }

    @SubscribeEvent
    public void onChannelUpdateAppliedTags(ChannelUpdateAppliedTagsEvent event) {
        ThreadChannel threadChannel = event.getChannel().asThreadChannel();
        Message startMessage = threadChannel.retrieveStartMessage().complete();

        if (startMessage != null) {
            insertSuggestion(threadChannel, startMessage);
        }
    }

    /**
     * Creates a {@link Suggestion} object from a {@link Message} and {@link ThreadChannel}.
     *
     * @param message       The message to create the suggestion object from.
     * @param threadChannel The thread channel to create the suggestion object from.
     *
     * @return The created {@link Suggestion} object.
     */
    private Suggestion createSuggestionObject(Message message, ThreadChannel threadChannel) {
        List<String> postTags = threadChannel.getAppliedTags().stream().map(BaseForumTag::getName).toList();
        Map<String, Integer> reactions = message.getReactions().stream().collect(Collectors.toMap(reaction -> reaction.getEmoji().getName(), MessageReaction::getCount));
        return new Suggestion(message.getId(), message.getContentRaw(), message.getAuthor().getId(), threadChannel.retrieveStartMessage().complete().getId(), new Suggestion.Channel(threadChannel.getOwnerId(), threadChannel.getName()), reactions, postTags, message.getTimeCreated());
    }

    /**
     * Insert a new {@link Suggestion} into the {@link SuggestionCache}.
     *
     * @param threadChannel The {@link ThreadChannel} the suggestion was created in.
     * @param message       The {@link Message} that was created.
     */
    private void insertSuggestion(ThreadChannel threadChannel, Message message) {
        if (!SkyBlockNerdsAPI.getCommandLine().hasOption("redisUri")) {
            return;
        }

        GuildConfiguration guildConfiguration = ConfigurationManager.loadConfig(GuildConfiguration.class);
        ForumChannel parentChannel = threadChannel.getParentChannel().asForumChannel();

        if (guildConfiguration.getSuggestionForumIds().contains(parentChannel.getId())) {
            Message startMessage = threadChannel.retrieveStartMessage().complete();

            if (!startMessage.getId().equalsIgnoreCase(message.getId())) {
                return;
            }

            DiscordBot.getSuggestionCache().insertSuggestion(message.getId(), createSuggestionObject(message, threadChannel));
            log.debug("Inserted suggestion from channel " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " by " + StringUtils.formatNameWithId(message.getAuthor().getName(), message.getAuthor().getId()) + ": " + threadChannel.getName() + " into Redis");
        }
    }
}
