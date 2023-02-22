package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.ForumChannelCurator;

import java.util.*;
import java.util.stream.Stream;

@Log4j2
public class CurateFeature extends BotFeature {

    private final Timer timer = new Timer();

    @Override
    public void onStart() {
        if (NerdBotApp.getBot().getConfig().getSuggestionForumIds() == null) {
            log.info("Not starting CurateFeature as 'suggestionForumIds' could not be found in the configuration file!");
            return;
        }

        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
                    Database database = NerdBotApp.getBot().getDatabase();
                    final Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(NerdBotApp.getBot().isReadOnly());

                    Stream.concat(
                            Arrays.stream(NerdBotApp.getBot().getConfig().getSuggestionForumIds()).map(forumId -> Pair.of(forumId, false)),
                            Arrays.stream(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).map(forumId -> Pair.of(forumId, true))
                    ).forEach(suggestionForum -> {
                        String id = suggestionForum.getLeft();
                        boolean alpha = suggestionForum.getRight();
                        ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(id);
                        log.info("Processing" + (alpha ? " alpha" : "") + " suggestion forum channel with ID " + id + ".");

                        if (forumChannel == null) {
                            log.error("Couldn't find the suggestion forum channel with ID " + id + "!");
                            return;
                        }

                        List<GreenlitMessage> result = forumChannelCurator.curate(forumChannel);
                        if (result.isEmpty()) {
                            log.info("No new suggestions were greenlit from ID " + id + " this time!");
                        } else {
                            log.info("Greenlit " + result.size() + " new suggestions from ID " + id + ". Took " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                        }

                        // Update Database
                        result.forEach(greenlitMessage -> database.upsertDocument(
                                database.getCollection(
                                        "greenlit_messages",
                                        GreenlitMessage.class
                                ),
                                "messageId",
                                greenlitMessage.getMessageId(),
                                greenlitMessage
                        ));
                        log.info("Inserted " + result.size() + " new greenlit messages into the database!");
                    });
                });
            }
        }, 30_000L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {
        this.timer.cancel();
    }
}
