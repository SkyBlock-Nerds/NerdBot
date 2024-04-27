package net.hypixel.skyblocknerds.discordbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.internal.entities.ForumTagImpl;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.curator.Curator;
import net.hypixel.skyblocknerds.api.curator.configuration.CuratorConfiguration;
import net.hypixel.skyblocknerds.database.objects.suggestion.GreenlitSuggestion;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.cache.EmojiCache;
import net.hypixel.skyblocknerds.utilities.StreamUtils;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
public class SuggestionCurator extends Curator<ForumChannel, GreenlitSuggestion> {

    private final CuratorConfiguration curatorConfiguration;
    private final Map<ThreadChannel, Message> threadStarterMessages = new HashMap<>();

    public SuggestionCurator() {
        super(DiscordBot.getCommandLine().hasOption("readOnly"));
        this.curatorConfiguration = ConfigurationManager.loadConfig(CuratorConfiguration.class);
    }

    @Override
    public void execute(ForumChannel forumChannel) {
        log.info("Curating forum channel: " + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()));

        long start = System.currentTimeMillis();
        List<ThreadChannel> threadChannels = new ArrayList<>();

        StreamUtils.combineStreams(forumChannel.getThreadChannels().stream().parallel(), forumChannel.retrieveArchivedPublicThreadChannels().stream().parallel())
                .filter(threadChannel -> {
                    long suggestionAge = System.currentTimeMillis() - threadChannel.getTimeCreated().toInstant().toEpochMilli();
                    if (suggestionAge > curatorConfiguration.getMaximumAgeConsidered()) {
                        return false;
                    }

                    boolean alreadyAcknowledged = threadChannel.getAppliedTags().stream().noneMatch(tag -> {
                        return !tag.getName().equalsIgnoreCase("Greenlit") || tag.getName().equalsIgnoreCase("Reviewed");
                    });

                    if (alreadyAcknowledged) {
                        return false;
                    }

                    try {
                        Message message = threadChannel.retrieveStartMessage().complete();
                        List<MessageReaction> reactions = message.getReactions();
                        int agrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("agree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0);
                        int disagrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("disagree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0);

                        threadStarterMessages.put(threadChannel, message);

                        return agrees >= curatorConfiguration.getMinimumReactionsRequired() || getRatio(agrees, disagrees) >= curatorConfiguration.getMinimumReactionRatio();
                    } catch (Exception exception) {
                        log.error("Failed to retrieve data for thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));
                        return false;
                    }
                })
                .forEach(threadChannels::add);

        long end = System.currentTimeMillis();

        log.info("Found " + StringUtils.formatNumberWithCommas(threadChannels.size()) + " threads in forum channel "
                + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()) + " in " + (end - start) + "ms");

        threadChannels.forEach(threadChannel -> {
            setIndex(threadChannels.indexOf(threadChannel) + 1);
            setTotal(threadChannels.size());

            String index = "[Thread " + getIndex() + "/" + getTotal() + "] ";

            if (isReadOnly()) {
                log.info(index + "Skipping greenlighting thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " because the bot is in read-only mode");
                return;
            }

            log.info(index + "Greenlighting thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));

            Message message = threadStarterMessages.get(threadChannel);
            GreenlitSuggestion greenlitSuggestion = GreenlitSuggestion.builder()
                    .userId(threadChannel.getOwnerId())
                    .messageId(threadChannel.getId())
                    .greenlitMessageId(message.getId())
                    .suggestionTitle(threadChannel.getName())
                    .suggestionContent(message.getContentRaw())
                    .suggestionUrl(threadChannel.getJumpUrl())
                    .suggestionTimestamp(threadChannel.getTimeCreated().toInstant().toEpochMilli())
                    .agrees(message.getReactions().stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("agree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0))
                    .disagrees(message.getReactions().stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("disagree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0))
                    .tags(threadChannel.getAppliedTags().stream().map(BaseForumTag::getName).toList())
                    .build();

            // Add the Greenlit tag if < 5 tags present, otherwise remove a tag and add it
            List<ForumTag> tags = new ArrayList<>(threadChannel.getAppliedTags());
            tags.add(new ForumTagImpl(123L));
            threadChannel.getManager().setAppliedTags(tags).complete();
            log.info("Greenlit thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));
        });

        threadStarterMessages.clear();
    }
}
