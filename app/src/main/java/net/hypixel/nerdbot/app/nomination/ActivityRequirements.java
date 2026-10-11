package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;

/**
 * Activity thresholds over a window of days, and a member's counts against them. The promotion
 * sweep and {@code /promotion} both measure with {@link #forPromotion}, so the numbers a member sees
 * are the ones the sweep uses.
 */
public record ActivityRequirements(int windowDays, int requiredMessages, int requiredVotes, int requiredComments) {

    public static ActivityRequirements forPromotion(RoleConfig roleConfig) {
        return new ActivityRequirements(
            roleConfig.getDaysRequiredForVoteHistory(),
            roleConfig.getMinimumMessagesRequiredForPromotion(),
            roleConfig.getMinimumVotesRequiredForPromotion(),
            roleConfig.getMinimumCommentsRequiredForPromotion());
    }

    public Progress measure(LastActivity activity) {
        return new Progress(this,
            activity.getTotalMessageCount(windowDays),
            activity.getTotalVotes(windowDays),
            activity.getTotalComments(windowDays));
    }

    public record Progress(ActivityRequirements requirements, int messages, int votes, int comments) {

        public boolean messagesMet() {
            return messages >= requirements.requiredMessages();
        }

        public boolean votesMet() {
            return votes >= requirements.requiredVotes();
        }

        public boolean commentsMet() {
            return comments >= requirements.requiredComments();
        }

        /** How many of the three thresholds are met, 0 to 3. */
        public int met() {
            return (messagesMet() ? 1 : 0) + (votesMet() ? 1 : 0) + (commentsMet() ? 1 : 0);
        }
    }
}
