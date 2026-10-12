package net.hypixel.nerdbot.app.nomination;

/**
 * What one sweep did with every stored member.
 *
 * @param acted            members nominated or warned
 * @param alreadyThisMonth members already nominated or warned this month
 * @param notDue           members below the bar for a nomination, or active enough not to be warned
 * @param ineligible       members without the role the sweep is for
 * @param missingMember    stored members who aren't in the guild
 * @param failed           members whose message Discord didn't accept, or whose check threw; they
 *                         weren't recorded, so the next run tries them again
 */
public record SweepReport(String label, int scanned, int acted, int alreadyThisMonth, int notDue, int ineligible,
                          int missingMember, int failed, long durationMs) {
}
