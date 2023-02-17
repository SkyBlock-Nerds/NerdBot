package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.ForumChannelCurator;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Log4j2
public class CurateFeature extends BotFeature {

    @Override
    public void onStart() {
        if (NerdBotApp.getBot().getConfig().getSuggestionForumId() == null) {
            log.info("Not starting CurateFeature as 'suggestionForumId' could not be found in the configuration file!");
            return;
        }

        Database database = NerdBotApp.getBot().getDatabase();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(NerdBotApp.getBot().isReadOnly());
                ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(NerdBotApp.getBot().getConfig().getSuggestionForumId());
                if (forumChannel == null) {
                    log.error("Couldn't find the suggestion forum channel from the bot config!");
                    return;
                }

                NerdBotApp.EXECUTOR_SERVICE.execute(() -> {
                    List<GreenlitMessage> result = forumChannelCurator.curate(forumChannel);
                    if (result.isEmpty()) {
                        log.info("No new suggestions were greenlit this time!");
                    } else {
                        log.info("Greenlit " + result.size() + " new suggestions in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                    }
                    result.forEach(greenlitMessage -> {
                        database.upsertDocument(database.getCollection("greenlit_messages", GreenlitMessage.class), "messageId", greenlitMessage.getMessageId(), greenlitMessage);
                    });
                    log.info("Inserted " + result.size() + " new greenlit messages into the database!");
                });
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 30_000L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {

    }
}
