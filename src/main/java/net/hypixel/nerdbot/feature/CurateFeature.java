package net.hypixel.nerdbot.feature;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.util.Logger;
import net.hypixel.nerdbot.util.Region;

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
                Curator curator = new Curator(25, Database.getInstance().getChannelGroups());
                curator.curate();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, Region.isDev() ? 5_000L : 30_000L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {

    }

}
