package net.hypixel.nerdbot.app.nomination;

import java.time.YearMonth;
import java.util.Optional;

/** Which month each monthly sweep last finished in, so one missed while the bot was down still runs. */
public interface SweepLog {

    Optional<YearMonth> lastRun(String sweep);

    void recordRun(String sweep, YearMonth month);
}
