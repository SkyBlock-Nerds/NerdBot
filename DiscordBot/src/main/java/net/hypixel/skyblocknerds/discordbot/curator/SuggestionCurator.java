package net.hypixel.skyblocknerds.discordbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.curator.Curator;
import net.hypixel.skyblocknerds.api.curator.configuration.CuratorConfiguration;
import net.hypixel.skyblocknerds.database.objects.suggestion.GreenlitSuggestion;
import net.hypixel.skyblocknerds.discordbot.DiscordBot;
import net.hypixel.skyblocknerds.discordbot.cache.EmojiCache;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SuggestionCurator extends Curator<ForumChannel, GreenlitSuggestion> {

    private final CuratorConfiguration curatorConfiguration;

    public SuggestionCurator() {
        super(DiscordBot.getCommandLine().hasOption("readOnly"));
        this.curatorConfiguration = ConfigurationManager.loadConfig(CuratorConfiguration.class);
    }

    @Override
    public void execute(ForumChannel forumChannel) {
        log.info("Curating forum channel: " + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()));

        long start = System.currentTimeMillis();
        List<ThreadChannel> threadChannels = new ArrayList<>(forumChannel.getThreadChannels());
        forumChannel.retrieveArchivedPublicThreadChannels().stream()
                .filter(threadChannel -> {
                    boolean alreadyAcknowledged = threadChannel.getAppliedTags().stream().noneMatch(tag -> {
                        return tag.getName().equalsIgnoreCase("Greenlit") || tag.getName().equalsIgnoreCase("Reviewed");
                    });

                    if (alreadyAcknowledged) {
                        log.info("Thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " has already been reviewed!");
                        return true;
                    }

                    try {
                        Message message = threadChannel.retrieveStartMessage().complete();
                        List<MessageReaction> reactions = message.getReactions();
                        int agrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("agree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0);
                        int disagrees = reactions.stream().filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName("disagree").orElse(null))).findFirst().map(MessageReaction::getCount).orElse(0);

                        log.info("Thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " has " + StringUtils.formatNumberWithCommas(agrees) + " agrees and " + StringUtils.formatNumberWithCommas(disagrees) + " disagrees (ratio: " + getRatio(agrees, disagrees) + "%)");

                        return agrees < curatorConfiguration.getMinimumReactionsRequired() || getRatio(agrees, disagrees) < curatorConfiguration.getMinimumReactionRatio();
                    } catch (Exception exception) {
                        log.error("Failed to retrieve data for thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));
                        return false;
                    }
                })
                .forEach(threadChannels::add);
        long end = System.currentTimeMillis();

        log.info("Found " + StringUtils.formatNumberWithCommas(threadChannels.size()) + " thread channels in forum channel "
                + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()) + " in " + (end - start) + "ms");

        threadChannels.forEach(threadChannel -> {
            setIndex(threadChannels.indexOf(threadChannel) + 1);
            setTotal(threadChannels.size());

            log.info("[Thread " + StringUtils.formatNumberWithCommas(getIndex()) + "/" + StringUtils.formatNumberWithCommas(getTotal()) + "] Curating thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));
        });
    }
}
