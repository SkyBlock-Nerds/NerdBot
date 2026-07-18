package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.app.testsupport.FakePunishmentStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PunishmentService} search and stats, exercised against an in-memory store.
 */
class PunishmentSearchAndStatsTest {

    @Test
    void searchByModeratorReturnsOnlyThatModeratorsPunishments() {
        PunishmentService service = new PunishmentService(new FakePunishmentStore()
            .seed(punishment("t1", "mod1", PunishmentType.WARNING))
            .seed(punishment("t2", "mod2", PunishmentType.WARNING)));

        List<Punishment> results = service.search("mod1", null, 10);

        assertEquals(1, results.size());
        assertEquals("mod1", results.getFirst().getModeratorUserId());
    }

    @Test
    void searchByModeratorAndTypeFiltersByType() {
        PunishmentService service = new PunishmentService(new FakePunishmentStore()
            .seed(punishment("t1", "mod1", PunishmentType.WARNING))
            .seed(punishment("t2", "mod1", PunishmentType.BAN)));

        List<Punishment> results = service.search("mod1", PunishmentType.BAN, 10);

        assertEquals(1, results.size());
        assertEquals(PunishmentType.BAN, results.getFirst().getType());
    }

    @Test
    void searchByTypeOnlyScansEveryModerator() {
        PunishmentService service = new PunishmentService(new FakePunishmentStore()
            .seed(punishment("t1", "mod1", PunishmentType.BAN))
            .seed(punishment("t2", "mod2", PunishmentType.BAN))
            .seed(punishment("t3", "mod1", PunishmentType.WARNING)));

        List<Punishment> results = service.search(null, PunishmentType.BAN, 10);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(punishment -> punishment.getType() == PunishmentType.BAN));
    }

    @Test
    void searchRespectsLimit() {
        FakePunishmentStore store = new FakePunishmentStore();
        for (int i = 0; i < 15; i++) {
            store.seed(punishment("t" + i, "mod1", PunishmentType.WARNING));
        }

        assertEquals(10, new PunishmentService(store).search("mod1", null, 10).size());
    }

    @Test
    void searchWithNoFiltersReturnsEmpty() {
        PunishmentService service = new PunishmentService(new FakePunishmentStore()
            .seed(punishment("t1", "mod1", PunishmentType.WARNING)));

        assertTrue(service.search(null, null, 10).isEmpty());
    }

    @Test
    void statsTalliesTotalAndPerTypeOmittingZeros() {
        PunishmentService service = new PunishmentService(new FakePunishmentStore()
            .seed(punishment("user", "mod1", PunishmentType.WARNING))
            .seed(punishment("user", "mod1", PunishmentType.WARNING))
            .seed(punishment("user", "mod2", PunishmentType.BAN))
            .seed(punishment("someone-else", "mod1", PunishmentType.KICK)));

        PunishmentStats stats = service.stats("user");

        assertEquals(3, stats.total());
        assertEquals(Map.of(PunishmentType.WARNING, 2L, PunishmentType.BAN, 1L), stats.countsByType());
    }

    private static Punishment punishment(String target, String moderator, PunishmentType type) {
        return new Punishment(target, moderator, type, "reason", "notes");
    }
}
