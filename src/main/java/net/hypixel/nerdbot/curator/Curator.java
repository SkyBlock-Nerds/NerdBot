package net.hypixel.nerdbot.curator;

import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.bot.Bot;
import net.hypixel.nerdbot.api.database.GreenlitMessage;
import net.hypixel.nerdbot.util.Logger;

import java.util.List;

public abstract class Curator<T> {

    private long startTime, endTime;

    public abstract List<GreenlitMessage> curate(List<T> list);

    public void log(String message) {
        Logger.info("[" + super.getClass().getSimpleName() + "] " + message);
    }

    public double getRatio(int positiveReactions, int negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }
        return (double) positiveReactions / (positiveReactions + negativeReactions) * 100;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public Bot getBot() {
        return NerdBotApp.getBot();
    }
}
