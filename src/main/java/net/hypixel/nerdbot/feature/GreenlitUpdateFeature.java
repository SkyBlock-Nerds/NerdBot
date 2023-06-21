package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.EmojiConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
public class GreenlitUpdateFeature extends BotFeature {

    private static final String[] INCLUDED_TAGS = {
            "Greenlit",
            "Docced"
    };

    @Override
    public void onStart() {
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Stream.concat(
                        Arrays.stream(NerdBotApp.getBot().getConfig().getSuggestionForumIds()).map(forumId -> Pair.of(forumId, false)),
                        Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).map(forumId -> Pair.of(forumId, true))
                ).filter(pair -> Objects.nonNull(pair.getLeft()))
                .forEach(suggestionForum -> {
                    if (NerdBotApp.getBot().isReadOnly()) {
                        log.info("Bot is in read-only mode, skipping greenlit suggestion update task!");
                        return;
                    }

                    String id = suggestionForum.getLeft();
                    boolean alpha = suggestionForum.getRight();
                    ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(id);

                    log.info("Processing" + (alpha ? " alpha" : "") + " suggestion forum channel with ID " + id + ".");

                    if (forumChannel == null) {
                        log.error("Couldn't find the suggestion forum channel with ID " + id + "!");
                        return;
                    }

                    List<ThreadChannel> threads = new ArrayList<>(forumChannel.getThreadChannels()
                            .stream()
                            .filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).anyMatch(tag -> Arrays.asList(INCLUDED_TAGS).contains(tag)))
                            .toList());
                    List<ThreadChannel> archived = forumChannel.retrieveArchivedPublicThreadChannels().complete();

                    threads.addAll(
                        archived.stream()
                            .filter(threadChannel -> !threads.contains(threadChannel))
                            .filter(threadChannel -> threadChannel.getAppliedTags().stream().map(ForumTag::getName).anyMatch(tag -> Arrays.asList(INCLUDED_TAGS).contains(tag)))
                            .toList()
                    );


                    if (threads.isEmpty()) {
                        log.info("No greenlit threads found in the suggestion forum channel!");
                        return;
                    }

                    Database database = NerdBotApp.getBot().getDatabase();
                    if (!database.isConnected()) {
                        log.info("Database is not connected, skipping greenlit message update!");
                        return;
                    }

                    List<GreenlitMessage> greenlits = database.getCollection("greenlit_messages", GreenlitMessage.class).find().into(new ArrayList<>());
                    if (greenlits.isEmpty()) {
                        log.info("No greenlit messages found in the database to update!");
                        return;
                    }

                    log.info("Found " + forumChannel.getThreadChannels().size() + " threads in the suggestion forum channel!");
                    log.info("Found " + threads.size() + " unarchived greenlit threads in the suggestion forum channel!");
                    log.info("Found " + archived.size() + " archived threads in the suggestion forum channel!");

                    greenlits.forEach(greenlitMessage -> {
                        log.info("Processing greenlit message '" + greenlitMessage.getSuggestionTitle() + "' (ID: " + greenlitMessage.getMessageId() + ")");
                        ThreadChannel thread = threads.stream().filter(threadChannel -> threadChannel.getId().equals(greenlitMessage.getMessageId()) || threadChannel.getName().equalsIgnoreCase(greenlitMessage.getSuggestionTitle())).findFirst().orElse(null);
                        if (thread == null) {
                            log.warn("Couldn't find thread for greenlit message '" + greenlitMessage.getSuggestionTitle() + "' (ID: " + greenlitMessage.getMessageId() + ")!");
                            return;
                        }

                        log.info("Found matching thread for greenlit message " + greenlitMessage.getMessageId() + ": '" + thread.getName() + "' (ID: " + thread.getId() + ")");

                        thread.getHistoryFromBeginning(1).queue(messageHistory -> {
                            log.info("Retrieved history for thread '" + thread.getName() + "' (ID: " + thread.getId() + ")");
                            Message message = messageHistory.getRetrievedHistory().get(0);
                            if (message == null) {
                                log.error("Message for thread '" + thread.getName() + "' (ID: " + thread.getId() + ") is null!");
                                return;
                            }

                            EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();
                            List<MessageReaction> reactions = message.getReactions()
                                    .stream()
                                    .filter(reaction -> reaction.getEmoji().getType() == Emoji.Type.CUSTOM)
                                    .toList();

                            Map<String, Integer> votes = Stream.of(
                                            emojiConfig.getAgreeEmojiId(),
                                            emojiConfig.getNeutralEmojiId(),
                                            emojiConfig.getDisagreeEmojiId()
                                    )
                                    .map(emojiId -> Pair.of(
                                            emojiId,
                                            reactions.stream()
                                                    .filter(reaction -> reaction.getEmoji()
                                                            .asCustom()
                                                            .getId()
                                                            .equalsIgnoreCase(emojiId)
                                                    )
                                                    .mapToInt(MessageReaction::getCount)
                                                    .findFirst()
                                                    .orElse(0)
                                    ))
                                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

                            int agree = votes.get(emojiConfig.getAgreeEmojiId());
                            int neutral = votes.get(emojiConfig.getNeutralEmojiId());
                            int disagree = votes.get(emojiConfig.getDisagreeEmojiId());
                            greenlitMessage.setSuggestionTitle(thread.getName());
                            greenlitMessage.setSuggestionContent(message.getContentRaw());
                            greenlitMessage.setTags(thread.getAppliedTags().stream().map(BaseForumTag::getName).toList());
                            greenlitMessage.setAgrees(agree);
                            greenlitMessage.setNeutrals(neutral);
                            greenlitMessage.setDisagrees(disagree);
                            greenlitMessage.setNeutrals(neutral);

                            database.upsertDocument(database.getCollection("greenlit_messages", GreenlitMessage.class), "messageId", greenlitMessage.getMessageId(), greenlitMessage);
                            log.info("Updated greenlit message '" + greenlitMessage.getSuggestionTitle() + "' (ID: " + greenlitMessage.getMessageId() + ") in the database!");
                        }, throwable -> log.error("Failed to retrieve history for thread '" + thread.getName() + "' (ID: " + thread.getId() + ")", throwable));
                    });
                });
            }
        }, 0L, TimeUnit.HOURS.toMillis(1L));
    }

    @Override
    public void onEnd() {
        this.timer.cancel();
    }
}
