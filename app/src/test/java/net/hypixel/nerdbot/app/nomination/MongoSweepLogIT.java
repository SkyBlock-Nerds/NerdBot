package net.hypixel.nerdbot.app.nomination;

import com.mongodb.client.MongoCollection;
import net.hypixel.nerdbot.app.testsupport.MongoTestDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The sweep log against a real MongoDB. */
class MongoSweepLogIT {

    private MongoCollection<Document> runs;
    private MongoSweepLog log;

    @BeforeEach
    void setUp() {
        runs = MongoTestDatabase.client().getDatabase(MongoTestDatabase.freshDatabaseName()).getCollection("sweep_runs");
        log = new MongoSweepLog(() -> runs);
    }

    @Test
    void nothingHasRunAtFirst() {
        assertEquals(Optional.empty(), log.lastRun("nominations"));
    }

    @Test
    void theLatestRunReplacesTheOneBefore() {
        log.recordRun("nominations", YearMonth.of(2026, 9));
        log.recordRun("nominations", YearMonth.of(2026, 10));

        assertEquals(Optional.of(YearMonth.of(2026, 10)), log.lastRun("nominations"));
        assertEquals(1, runs.countDocuments());
    }

    @Test
    void sweepsAreLoggedSeparately() {
        log.recordRun("nominations", YearMonth.of(2026, 10));
        log.recordRun("inactivity-checks", YearMonth.of(2026, 9));

        assertEquals(Optional.of(YearMonth.of(2026, 9)), log.lastRun("inactivity-checks"));
    }
}
