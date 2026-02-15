package net.hypixel.nerdbot.app.nomination;

public record InactivitySweepReport(String label, int scanned, int warnedThisMonth, int skippedAlreadyThisMonth,
                                    int ineligible, int missingMember, long durationMs) {
}