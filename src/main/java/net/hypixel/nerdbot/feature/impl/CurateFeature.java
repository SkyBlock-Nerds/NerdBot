package net.hypixel.nerdbot.feature.impl;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.Channel;
import net.hypixel.nerdbot.curator.Curator;
import net.hypixel.nerdbot.feature.BotFeature;

import java.util.Timer;
import java.util.TimerTask;

public class CurateFeature extends BotFeature {

    @Override
    public void onStart() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                Curator curator = new Curator(100, NerdBotApp.getBot().getJDA().getTextChannelById(Channel.SUGGESTIONS.getId()));
                curator.curate();
                curator.applyEmoji();
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0L, NerdBotApp.getBot().getConfig().getInterval());
    }

    @Override
    public void onEnd() {

    }

}
