package net.hypixel.skyblocknerds.discordbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.configuration.ConfigurationManager;
import net.hypixel.skyblocknerds.api.curator.Curator;
import net.hypixel.skyblocknerds.api.curator.configuration.CuratorConfiguration;
import net.hypixel.skyblocknerds.database.objects.suggestion.GreenlitSuggestion;
import net.hypixel.skyblocknerds.database.repository.RepositoryManager;
import net.hypixel.skyblocknerds.database.repository.impl.GreenlitSuggestionRepository;
import net.hypixel.skyblocknerds.discordbot.cache.EmojiCache;
import net.hypixel.skyblocknerds.utilities.StreamUtils;
import net.hypixel.skyblocknerds.utilities.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Log4j2
public class SuggestionCurator extends Curator<ForumChannel, GreenlitSuggestion> {

    private final CuratorConfiguration curatorConfiguration;
    private final Map<ThreadChannel, Message> threadStarterMessages = new HashMap<>();

    public SuggestionCurator() {
        super(SkyBlockNerdsAPI.getCommandLine().hasOption("readOnly"));
        this.curatorConfiguration = ConfigurationManager.loadConfig(CuratorConfiguration.class);
    }

    @Override
    public void execute(ForumChannel forumChannel) {
        log.info("Curating forum channel: " + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()));

        setStartTime(System.currentTimeMillis());

        ForumTag greenlitTag = forumChannel.getAvailableTagsByName(curatorConfiguration.getGreenlitTagName(), true).stream()
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Greenlit tag not found in forum channel " + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId())));

        List<ThreadChannel> threadChannels = new ArrayList<>();

        StreamUtils.combineStreams(forumChannel.getThreadChannels().stream().parallel(), forumChannel.retrieveArchivedPublicThreadChannels().stream().parallel())
            .filter(threadChannel -> {
                long suggestionAge = System.currentTimeMillis() - threadChannel.getTimeCreated().toInstant().toEpochMilli();

                if (curatorConfiguration.getMaximumAgeConsidered() > 0 && suggestionAge > curatorConfiguration.getMaximumAgeConsidered()) {
                    return false;
                }

                boolean alreadyAcknowledged = threadChannel.getAppliedTags().stream().anyMatch(tag -> {
                    return tag.getName().equalsIgnoreCase(curatorConfiguration.getGreenlitTagName()) || tag.getName().equalsIgnoreCase(curatorConfiguration.getReviewedTagName());
                });

                if (alreadyAcknowledged) {
                    return false;
                }

                try {
                    Message message = threadChannel.retrieveStartMessage().complete();
                    int agrees = getReactionCount(message, "agree");
                    int disagrees = getReactionCount(message, "disagree");

                    if (agrees >= curatorConfiguration.getMinimumReactionsRequired() && getRatio(agrees, disagrees) >= curatorConfiguration.getMinimumReactionRatio()) {
                        threadStarterMessages.put(threadChannel, message);
                        return true;
                    }

                    return false;
                } catch (Exception exception) {
                    return false;
                }
            })
            .forEach(threadChannels::add);

        setTotal(threadChannels.size());

        log.info("Found " + StringUtils.formatNumberWithCommas(threadChannels.size()) + " threads in forum channel "
            + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()));

        threadChannels.forEach(threadChannel -> {
            setIndex(threadChannels.indexOf(threadChannel) + 1);

            String prefix = "[Thread " + getIndex() + "/" + getTotal() + "] ";

            if (isReadOnly()) {
                log.info(prefix + "Skipping greenlighting thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " because the bot is in read-only mode");
                return;
            }

            log.info(prefix + "Greenlighting thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));

            List<ForumTag> tags = new ArrayList<>(threadChannel.getAppliedTags());

            if (tags.size() >= 5) {
                ForumTag removedTag = tags.remove(tags.size() - 1);
                threadChannel.sendMessage("This thread has reached the maximum number of allowed tags, so the `" + removedTag.getName() + "` tag has been removed to make room for the `Greenlit` tag.").complete();
                log.debug(prefix + "Removed tag " + removedTag.getName() + " from thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " to make room for the Greenlit tag");
            }

            tags.add(0, greenlitTag);

            try {
                if (threadChannel.isArchived()) {
                    threadChannel.getManager().setArchived(false).complete();
                    log.debug(prefix + "Unarchived thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " before greenlighting");
                }

                threadChannel.getManager().setAppliedTags(tags).complete();

                if (curatorConfiguration.isArchiveGreenlitThreads()) {
                    threadChannel.getManager().setArchived(true).complete();
                    log.debug(prefix + "Archived thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " after greenlighting due to configuration setting");
                }

                if (curatorConfiguration.isLockGreenlitThreads()) {
                    threadChannel.getManager().setLocked(true).complete();
                    log.debug("Locked thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " after greenlighting due to configuration setting");
                }

                Message message = threadStarterMessages.get(threadChannel);
                GreenlitSuggestion greenlitSuggestion = GreenlitSuggestion.builder()
                    .userId(threadChannel.getOwnerId())
                    .messageId(threadChannel.getId())
                    .greenlitMessageId(message.getId())
                    .suggestionTitle(threadChannel.getName())
                    .suggestionContent(message.getContentRaw())
                    .suggestionUrl(threadChannel.getJumpUrl())
                    .suggestionTimestamp(threadChannel.getTimeCreated().toInstant().toEpochMilli())
                    .agrees(getReactionCount(message, "agree"))
                    .disagrees(getReactionCount(message, "disagree"))
                    .tags(threadChannel.getAppliedTags().stream().map(BaseForumTag::getName).toList())
                    .build();

                GreenlitSuggestionRepository greenlitSuggestionRepository = RepositoryManager.getInstance().getRepository(GreenlitSuggestionRepository.class);
                greenlitSuggestionRepository.cacheObject(greenlitSuggestion);
                log.info(prefix + "Greenlit thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()));
            } catch (InsufficientPermissionException exception) {
                log.warn(prefix + "Failed to greenlight thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()) + " due to insufficient permissions");
            } catch (Exception exception) {
                log.error(prefix + "Failed to greenlight thread " + StringUtils.formatNameWithId(threadChannel.getName(), threadChannel.getId()), exception);
            }
        });

        setEndTime(System.currentTimeMillis());
        threadStarterMessages.clear();
        log.info("Finished curating forum channel " + StringUtils.formatNameWithId(forumChannel.getName(), forumChannel.getId()) + " in " + (getEndTime() - getStartTime()) + "ms");
    }

    private int getReactionCount(Message message, String reactionName) {
        return message.getReactions().stream()
            .filter(messageReaction -> messageReaction.getEmoji().equals(EmojiCache.getEmojiByName(reactionName).orElse(null)))
            .findFirst()
            .map(MessageReaction::getCount)
            .orElse(0);
    }
}