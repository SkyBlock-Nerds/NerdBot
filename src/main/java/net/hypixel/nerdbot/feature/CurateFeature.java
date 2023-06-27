package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.TimerTask;
import java.util.stream.Stream;

@Log4j2
public class CurateFeature extends BotFeature {

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
                    Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(NerdBotApp.getBot().isReadOnly());
                    Stream<ForumChannel> suggestions = Util.concatStreams(NerdBotApp.getBot().getConfig().getSuggestionForumIds(), NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds())
                        .map(NerdBotApp.getBot().getJDA()::getForumChannelById)
                        .filter(Objects::nonNull);

                    suggestions.forEach(channel -> {
                        boolean alpha;

                        if (NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds() != null) {
                            alpha = Arrays.asList(NerdBotApp.getBot().getConfig().getAlphaSuggestionForumIds()).contains(channel.getId());
                        } else {
                            alpha = channel.getName().contains("alpha");
                        }

                        log.info("Processing" + (alpha ? " alpha" : "") + " suggestion forum channel '" + channel.getName() + "' (ID: " + channel + ")");

                        List<GreenlitMessage> result = forumChannelCurator.curate(channel);
                        if (result.isEmpty()) {
                            log.info("No new suggestions were greenlit from ID " + channel.getId() + " this time!");
                        } else {
                            log.info("Greenlit " + result.size() + " new suggestions from ID " + channel.getId() + ". Took " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                        }

                        // Update Database
                        result.forEach(greenlitMessage -> database.upsertDocument(database.getCollection("greenlit_messages", GreenlitMessage.class), "messageId", greenlitMessage.getMessageId(), greenlitMessage));
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
