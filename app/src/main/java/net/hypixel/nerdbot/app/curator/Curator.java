package net.hypixel.nerdbot.app.curator;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;

import java.util.List;

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

    public static double getRatio(double positiveReactions, double negativeReactions) {
        if (positiveReactions == 0 && negativeReactions == 0) {
            return 0;
        }
        return positiveReactions / (positiveReactions + negativeReactions) * 100;
    }

    /**
     * Whether a suggestion's votes qualify it to be greenlit: it needs at least {@code agreeThreshold}
     * agree reactions <em>and</em> an agree/disagree {@link #getRatio(double, double) ratio} of at
     * least {@code ratioThreshold} percent.
     *
     * @param agree          the number of agree reactions
     * @param disagree       the number of disagree reactions
     * @param agreeThreshold the minimum agree reactions required
     * @param ratioThreshold the minimum agree/disagree ratio required, as a percentage
     * @return {@code true} if the suggestion meets both thresholds
     */
    public static boolean meetsGreenlitThreshold(int agree, int disagree, int agreeThreshold, double ratioThreshold) {
        return agree >= agreeThreshold && getRatio(agree, disagree) >= ratioThreshold;
    }

    public abstract List<GreenlitMessage> curate(T t);
}
