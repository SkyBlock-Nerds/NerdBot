package net.hypixel.nerdbot.app.nomination;

public record NominationSweepReport(String label, int scanned, int eligible, int nominated, int belowThreshold,
                                    int alreadyThisMonth, int ineligible, int missingMember, long durationMs) {
}