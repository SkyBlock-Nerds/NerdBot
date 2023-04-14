package net.hypixel.nerdbot.api.curator;

import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.greenlit.GreenlitMessage;

import java.util.List;

public abstract class Curator<T> {

    private long startTime;
    private long endTime;
    private final boolean readOnly;

    protected Curator(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public abstract List<GreenlitMessage> curate(T t);

    public double getRatio(double positiveReactions, double negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }
        return positiveReactions / (positiveReactions + negativeReactions) * 100;
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

    public boolean isReadOnly() {
        return readOnly;
    }

    public JDA getJDA() {
        return NerdBotApp.getBot().getJDA();
    }
}
