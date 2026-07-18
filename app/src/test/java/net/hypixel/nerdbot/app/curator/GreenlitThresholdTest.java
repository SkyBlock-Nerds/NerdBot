package net.hypixel.nerdbot.app.curator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure greenlit math on {@link Curator}: {@link Curator#getRatio} and
 * {@link Curator#meetsGreenlitThreshold}.
 */
class GreenlitThresholdTest {

    @Test
    void ratioIsZeroWhenThereAreNoReactions() {
        assertEquals(0.0, Curator.getRatio(0, 0), 0.0001);
    }

    @Test
    void ratioIsAgreeShareAsPercentage() {
        assertEquals(75.0, Curator.getRatio(3, 1), 0.0001);
    }

    @Test
    void meetsThresholdWhenBothAgreeCountAndRatioQualify() {
        // 10 agree / 2 disagree -> 83.3% ratio, threshold 5 agree and 50%.
        assertTrue(Curator.meetsGreenlitThreshold(10, 2, 5, 50.0));
    }

    @Test
    void failsWhenAgreeCountIsBelowThreshold() {
        // High ratio (100%) but only 3 agree against a threshold of 5.
        assertFalse(Curator.meetsGreenlitThreshold(3, 0, 5, 50.0));
    }

    @Test
    void failsWhenRatioIsBelowThreshold() {
        // Plenty of agree reactions but 10/30 = 33% ratio against a 50% threshold.
        assertFalse(Curator.meetsGreenlitThreshold(10, 20, 5, 50.0));
    }

    @Test
    void zeroReactionsNeverGreenlight() {
        // getRatio(0, 0) is 0, so even a zero agree threshold fails the ratio requirement.
        assertFalse(Curator.meetsGreenlitThreshold(0, 0, 0, 50.0));
    }
}
