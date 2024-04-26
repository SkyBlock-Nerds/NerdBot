package net.hypixel.skyblocknerds.api.curator;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Curator<T, O> {

    private final boolean readOnly;
    private boolean completed;
    private long startTime;
    private long endTime;
    private int index;
    private int total;
    private O currentObject;

    protected Curator(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public abstract void execute(T t);

    public static double getRatio(double positiveReactions, double negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }

        return positiveReactions / (positiveReactions + negativeReactions) * 100;
    }
}