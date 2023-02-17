package net.hypixel.nerdbot.curator;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ForumChannelCurator extends Curator<ForumChannel> {

    private final Database database = NerdBotApp.getBot().getDatabase();
    private final EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();

    public ForumChannelCurator(boolean readOnly) {
        super(readOnly);
    }

    @Override
    public List<GreenlitMessage> curate(ForumChannel forumChannel) {
        List<GreenlitMessage> output = new ArrayList<>();
        setStartTime(System.currentTimeMillis());

        if (!database.isConnected()) {
            setEndTime(System.currentTimeMillis());
            log.error("Couldn't curate messages as the database is not connected!");
            return output;
        }

        log.info("Curating forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ")");

        int index = 0;
        List<ThreadChannel> threads = forumChannel.getThreadChannels()
                .stream()
                .filter(threadChannel -> threadChannel.getAppliedTags().stream().anyMatch(tag -> !tag.getName().equalsIgnoreCase("greenlit")))
                .toList();

        log.info("Found " + threads.size() + " non-greenlit forum post(s)!");

        for (ThreadChannel thread : threads) {
            log.info("Curating thread '" + thread.getName() + "' (ID: " + thread.getId() + ") by " + thread.getOwner().getEffectiveName());

            MessageHistory history = thread.getHistoryFromBeginning(1).complete();
            Message message = history.getRetrievedHistory().get(0);

            if (message == null) {
                log.error("Message for thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is null!");
                return output;
            }

            log.info("Found message: " + message.getContentRaw());

            long agree = message.getReactions().stream().filter(reaction -> {
                System.out.println("Reaction: " + reaction.getEmoji().asCustom().getId());
                return reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getAgreeEmojiId());
            }).mapToLong(MessageReaction::getCount).sum();
            long disagree = message.getReactions().stream().filter(reaction -> {
                System.out.println("Reaction: " + reaction.getEmoji().asCustom().getId());
                return reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getDisagreeEmojiId());
            }).mapToLong(MessageReaction::getCount).sum();
            long neutral = message.getReactions().stream().filter(reaction -> {
                System.out.println("Reaction: " + reaction.getEmoji().asCustom().getId());
                return reaction.getEmoji().asCustom().getId().equalsIgnoreCase(emojiConfig.getNeutralEmojiId());
            }).mapToLong(MessageReaction::getCount).sum();

            log.info("Agree: " + agree + ", disagree: " + disagree + ", neutral: " + neutral);
        }

        setEndTime(System.currentTimeMillis());
        log.info("Curated forum channel: " + forumChannel.getName() + " (Channel ID: " + forumChannel.getId() + ") in " + (getEndTime() - getStartTime()) + "ms");

        return output;
    }
}
