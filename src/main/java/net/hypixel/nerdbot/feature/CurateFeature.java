package net.hypixel.nerdbot.feature;

import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.curator.Curator;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CurateFeature extends BotFeature {

    @Override
    public void onStart() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (Database.getInstance().getChannelGroup("DefaultSuggestions") == null) {
                    Logger.error("Couldn't find a default suggestions channel group!");
                    return;
                }

                Curator<ForumChannel> forumChannelCurator = new ForumChannelCurator(false);
                ForumChannel forumChannel = NerdBotApp.getBot().getJDA().getForumChannelById(NerdBotApp.getBot().getConfig().getSuggestionForumId());
                if (forumChannel == null) {
                    Logger.error("Couldn't find the suggestion forum channel from the bot config!");
                    return;
                }

                NerdBotApp.EXECUTOR_SERVICE.submit(() -> {
                    List<GreenlitMessage> result = forumChannelCurator.curate(List.of(forumChannel));

                    if (result.isEmpty()) {
                        Logger.info("No new suggestions were greenlit this time!");
                    } else {
                        Logger.info("Greenlit " + result.size() + " new suggestions in " + (forumChannelCurator.getEndTime() - forumChannelCurator.getStartTime()) + "ms!");
                    }

                    Database.getInstance().createOrUpdateGreenlitMessages(result);
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
