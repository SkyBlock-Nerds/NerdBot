package net.hypixel.nerdbot.app.nomination;

import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
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

    public static ActivityRequirements forInactivity(RoleConfig roleConfig) {
        return new ActivityRequirements(
            roleConfig.getDaysRequiredForInactivityCheck(),
            roleConfig.getMessagesRequiredForInactivityCheck(),
            roleConfig.getVotesRequiredForInactivityCheck(),
            roleConfig.getCommentsRequiredForInactivityCheck());
    }

    public static ActivityRequirements forGroup(RoleRestrictedChannelGroup group) {
        return new ActivityRequirements(
            group.getActivityCheckDays(),
            group.getMinimumMessagesForActivity(),
            group.getMinimumVotesForActivity(),
            group.getMinimumCommentsForActivity());
    }

    public Progress measure(LastActivity activity) {
        return new Progress(this,
            activity.getTotalMessageCount(windowDays),
            activity.getTotalVotes(windowDays),
            activity.getTotalComments(windowDays));
    }

    /** Counts only the activity in a restricted channel group's channels. */
    public Progress measureInGroup(LastActivity activity, String groupIdentifier) {
        return new Progress(this,
            activity.getRoleRestrictedChannelMessageCount(groupIdentifier, windowDays),
            activity.getRoleRestrictedChannelVoteCount(groupIdentifier, windowDays),
            activity.getRoleRestrictedChannelCommentCount(groupIdentifier, windowDays));
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
