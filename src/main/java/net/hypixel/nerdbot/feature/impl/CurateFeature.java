package net.hypixel.nerdbot.feature.impl;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.database.Database;
import net.hypixel.nerdbot.feature.BotFeature;
import net.hypixel.nerdbot.util.Logger;

import java.util.Timer;
import java.util.TimerTask;

public class CurateFeature extends BotFeature {

    @Override
    public void onStart() {
        if (Database.getInstance().getChannelGroup("DefaultSuggestions") == null) {
            Logger.error("Couldn't find a default suggestions channel group!");
            return;
        }

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Curator curator = new Curator(100, Database.getInstance().getChannelGroup("DefaultSuggestions"));
                curator.curate();
                if (!curator.getGreenlitMessages().isEmpty()) {
                    curator.applyEmoji();
                    curator.sendGreenlitToChannel();
                    curator.insertIntoDatabase();
                }
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {

    }

}
