package net.hypixel.nerdbot.app.nomination;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.hypixel.nerdbot.app.storage.SweepRuns;
import org.bson.Document;

import java.time.YearMonth;
import java.util.Optional;

/** One document per sweep in {@code sweep_runs}: {@code {_id: "nominations", month: "2026-10"}}. */
public class MongoSweepLog implements SweepLog {

    private final Provider<MongoCollection<Document>> runs;

    @Inject
    public MongoSweepLog(@SweepRuns Provider<MongoCollection<Document>> runs) {
        this.runs = runs;
    }

    @Override
    public Optional<YearMonth> lastRun(String sweep) {
        Document run = runs.get().find(Filters.eq("_id", sweep)).first();
        return run == null ? Optional.empty() : Optional.of(YearMonth.parse(run.getString("month")));
    }

    @Override
    public void recordRun(String sweep, YearMonth month) {
        runs.get().replaceOne(Filters.eq("_id", sweep), new Document("_id", sweep).append("month", month.toString()), new ReplaceOptions().upsert(true));
    }
}
