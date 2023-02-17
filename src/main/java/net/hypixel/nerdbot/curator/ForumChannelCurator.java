package net.hypixel.nerdbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel> {

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    @Override
    public List<GreenlitMessage> curate(ForumChannel forumChannel) {
        Database database = NerdBotApp.getBot().getDatabase();
        BotConfig config = NerdBotApp.getBot().getConfig();
        EmojiConfig emojiConfig = config.getEmojiConfig();
        ForumTag greenlitTag = forumChannel.getAvailableTagById(config.getTagConfig().getGreenlit());
        List<GreenlitMessage> output = new ArrayList<>();

        setStartTime(System.currentTimeMillis());

        if (!database.isConnected()) {
            setEndTime(System.currentTimeMillis());
            log.error("Couldn't curate messages as the database is not connected!");
            return output;
        }

        log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

        List<ThreadChannel> threads = forumChannel.getThreadChannels()
                .stream()
                .filter(threadChannel -> threadChannel.getAppliedTags().stream().anyMatch(tag -> !tag.getName().equalsIgnoreCase("greenlit")))
                .toList();

        log.info("Found " + threads.size() + " non-greenlit forum post(s)!");

        int index = 0;
        for (ThreadChannel thread : threads) {
            log.info("["+ (++index) + "/" + threads.size() + "] Curating thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");

            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);
            if (message == null) {
                log.error("Message for thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is null!");
                continue;
            }

            try {
                log.info("Checking reaction counts for message ID: " + message.getId());

                List<MessageReaction> reactions = message.getReactions()
                        .stream()
                        .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                        .toList();

                int agree = reactions.stream()
                        .filter(reaction -> reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getAgreeEmojiId()))
                        .mapToInt(MessageReaction::getCount)
                        .findFirst()
                        .orElse(0);
                int disagree = reactions.stream()
                        .filter(reaction -> reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getDisagreeEmojiId()))
                        .mapToInt(MessageReaction::getCount)
                        .findFirst()
                        .orElse(0);
                int neutral = reactions.stream()
                        .filter(reaction -> reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getNeutralEmojiId()))
                        .mapToInt(MessageReaction::getCount)
                        .findFirst()
                        .orElse(0);
                double ratio = getRatio(agree, neutral, disagree);

                log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") has " + agree + " agree reactions, " + neutral + " neutral reactions, and " + disagree + " disagree reactions with a ratio of " + ratio + "%");

                if ((agree < config.getMinimumThreshold()) || (ratio < config.getPercentage())) {
                    log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") does not meet the minimum requirements to be greenlit!");
                    continue;
                }

                log.info("Thread '" + thread.getName() + "' (ID: " + thread.getId() + ") meets the minimum requirements to be greenlit!");

                if (isReadOnly()) {
                    log.info("Skipping thread '" + thread.getName() + "' (ID: " + thread.getId() + ") as the curator is in read-only mode!");
                    continue;
                }

                List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                tags.add(greenlitTag);
                thread.getManager().setAppliedTags(tags).complete();

                GreenlitMessage greenlitMessage = GreenlitMessage.builder()
                        .agrees(agree)
                        .disagrees(disagree)
                        .messageId(message.getId())
                        .userId(message.getAuthor().getId())
                        .suggestionUrl(message.getJumpUrl())
                        .suggestionTitle(thread.getName())
                        .suggestionTimestamp(thread.getTimeCreated().toInstant().toEpochMilli())
                        .suggestionContent(message.getContentRaw())
                        .tags(thread.getAppliedTags().stream().map(BaseForumTag::getName).toList())
                        .build();
                output.add(greenlitMessage);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        setEndTime(System.currentTimeMillis());
        log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");

        return output;
    }
}
