package net.hypixel.nerdbot.feature;

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
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class GreenlitUpdateFeature extends BotFeature {

    @Override
    public void onStart() {
        Bot nerdBot = NerdBotApp.getBot();
        ForumChannel suggestions = nerdBot.getJDA().getForumChannelById(nerdBot.getConfig().getSuggestionForumId());
        if (suggestions == null) {
            log.error("Couldn't find the suggestion forum channel from the bot config!");
            return;
        }

        List<ThreadChannel> greenlitThreads = new ArrayList<>(suggestions.getThreadChannels().stream().filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).toList().contains("Greenlit")).toList());
        List<ThreadChannel> archived = suggestions.retrieveArchivedPublicThreadChannels().complete();

        log.info("Found " + suggestions.getThreadChannels().size() + " threads in the suggestion forum channel!");
        log.info("Found " + archived.size() + " archived threads in the suggestion forum channel!");

        greenlitThreads.addAll(archived.stream()
                .filter(threadChannel -> !greenlitThreads.contains(threadChannel))
                .filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).toList().contains("Greenlit"))
                .toList());

        Database database = nerdBot.getDatabase();
        if (!database.isConnected()) {
            log.info("Database is not connected, skipping greenlit message update!");
            return;
        }

        List<GreenlitMessage> greenlits = database.getCollection("greenlit_messages", GreenlitMessage.class).find().into(new ArrayList<>());
        if (greenlits.size() == 0) {
            log.info("No greenlit messages found in the database to update!");
            return;
        }

        log.info("Found " + greenlitThreads.size() + " total greenlit threads");
        log.info("Found " + greenlits.size() + " greenlit messages in the database!");

        greenlits.forEach(greenlitMessage -> {
            if (greenlitThreads.stream().anyMatch(threadChannel -> threadChannel.getId().equals(greenlitMessage.getMessageId()))) {
                ThreadChannel thread = greenlitThreads.stream().filter(threadChannel -> threadChannel.getId().equals(greenlitMessage.getMessageId())).findFirst().orElse(null);
                if (thread == null) {
                    return;
                }

                log.info("Found matching thread for greenlit message " + greenlitMessage.getMessageId() + ": '" + thread.getName() + "' (ID: " + thread.getId() + ")");

                MessageHistory history = thread.getHistoryFromBeginning(1).complete();
                Message message = history.getRetrievedHistory().get(0);
                if (message == null) {
                    log.error("Message for thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is null!");
                    return;
                }

                EmojiConfig emojiConfig = nerdBot.getConfig().getEmojiConfig();
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

                greenlitMessage.setSuggestionTitle(thread.getName());
                greenlitMessage.setSuggestionContent(message.getContentRaw());
                greenlitMessage.setTags(thread.getAppliedTags().stream().map(BaseForumTag::getName).toList());
                greenlitMessage.setAgrees(agree);
                greenlitMessage.setDisagrees(disagree);
                greenlitMessage.setNeutrals(neutral);

                database.upsertDocument(database.getCollection("greenlit_messages", GreenlitMessage.class), "messageId", greenlitMessage.getMessageId(), greenlitMessage);
                log.info("Updated greenlit message " + greenlitMessage.getMessageId() + " in the database!");
            }
        });
    }

    @Override
    public void onEnd() {

    }
}
