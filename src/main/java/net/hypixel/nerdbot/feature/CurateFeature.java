package net.hypixel.nerdbot.feature;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.util.Logger;

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

                Curator curator = new Curator(NerdBotApp.getBot().getConfig().getMessageLimit(), Database.getInstance().getChannelGroups(), Boolean.parseBoolean(System.getProperty("bot.readOnly", "false")));
                curator.curate();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 30_000L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {

    }

}
