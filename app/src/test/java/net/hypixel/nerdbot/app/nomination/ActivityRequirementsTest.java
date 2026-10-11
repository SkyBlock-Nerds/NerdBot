package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityRequirementsTest {

    private static final ActivityRequirements REQUIREMENTS = new ActivityRequirements(90, 10, 3, 2);

    @Test
    void promotionRequirementsComeFromTheRoleConfig() {
        RoleConfig roleConfig = new RoleConfig();
        roleConfig.setDaysRequiredForVoteHistory(60);
        roleConfig.setMinimumMessagesRequiredForPromotion(5);
        roleConfig.setMinimumVotesRequiredForPromotion(7);
        roleConfig.setMinimumCommentsRequiredForPromotion(9);

        assertEquals(new ActivityRequirements(60, 5, 7, 9), ActivityRequirements.forPromotion(roleConfig));
    }

    @Test
    void reachingAThresholdExactlyMeetsIt() {
        ActivityRequirements.Progress progress = new ActivityRequirements.Progress(REQUIREMENTS, 10, 3, 2);

        assertTrue(progress.messagesMet());
        assertTrue(progress.votesMet());
        assertTrue(progress.commentsMet());
        assertEquals(3, progress.met());
    }

    @Test
    void oneShortDoesNotMeetIt() {
        ActivityRequirements.Progress progress = new ActivityRequirements.Progress(REQUIREMENTS, 9, 2, 1);

        assertFalse(progress.messagesMet());
        assertFalse(progress.votesMet());
        assertFalse(progress.commentsMet());
        assertEquals(0, progress.met());
    }

    @Test
    void metCountsEachThresholdSeparately() {
        assertEquals(1, new ActivityRequirements.Progress(REQUIREMENTS, 0, 3, 0).met());
        assertEquals(2, new ActivityRequirements.Progress(REQUIREMENTS, 10, 0, 2).met());
    }

    @Test
    void measuringOnlyCountsActivityInsideTheWindow() {
        LastActivity activity = new LastActivity();
        long now = System.currentTimeMillis();
        activity.getSuggestionVoteHistoryMap().put("recent", now - Duration.ofDays(1).toMillis());
        activity.getSuggestionVoteHistoryMap().put("old", now - Duration.ofDays(120).toMillis());
        activity.getSuggestionCommentHistory().add(now - Duration.ofDays(2).toMillis());

        ActivityRequirements.Progress progress = REQUIREMENTS.measure(activity);

        assertEquals(1, progress.votes());
        assertEquals(1, progress.comments());
        assertEquals(0, progress.messages());
        assertEquals(REQUIREMENTS, progress.requirements());
    }
}
