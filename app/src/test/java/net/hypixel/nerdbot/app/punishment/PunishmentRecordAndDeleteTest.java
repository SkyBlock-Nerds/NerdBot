package net.hypixel.nerdbot.app.punishment;

import net.hypixel.nerdbot.app.testsupport.FakePunishmentStore;
import net.hypixel.nerdbot.app.testsupport.FakePunishmentStore.WriteOutcome;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentRecordAndDeleteTest {

    private final FakePunishmentStore store = new FakePunishmentStore();
    private final PunishmentService service = new PunishmentService(store);

    @Test
    void recordSavesAndReturnsThePunishment() {
        Punishment saved = service.record("target", "mod", PunishmentType.BAN, "spam", "second offence").join();

        assertEquals("target", saved.getTargetUserId());
        assertEquals("mod", saved.getModeratorUserId());
        assertEquals(PunishmentType.BAN, saved.getType());
        assertEquals("spam", saved.getReason());
        assertEquals("second offence", saved.getNotes());
        assertSame(saved, store.findById(saved.getPunishmentId()).orElseThrow());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n"})
    void blankNotesAreStoredAsNoNotes(String notes) {
        Punishment saved = service.record("target", "mod", PunishmentType.WARNING, "spam", notes).join();

        assertNull(saved.getNotes());
    }

    @Test
    void recordFailsWhenTheWriteIsNotAcknowledged() {
        store.writesWill(WriteOutcome.UNACKNOWLEDGED);

        CompletionException thrown = assertThrows(CompletionException.class,
            () -> service.record("target", "mod", PunishmentType.BAN, "spam", null).join());

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertTrue(store.getAll().isEmpty());
    }

    @Test
    void recordFailsWhenTheWriteFails() {
        store.writesWill(WriteOutcome.FAILED);

        assertThrows(CompletionException.class,
            () -> service.record("target", "mod", PunishmentType.BAN, "spam", null).join());
    }

    @Test
    void deleteRemovesThePunishment() {
        Punishment punishment = new Punishment("target", "mod", PunishmentType.KICK, "spam", null);
        store.seed(punishment);

        service.delete(punishment.getPunishmentId()).join();

        assertTrue(service.find(punishment.getPunishmentId()).isEmpty());
    }

    @Test
    void deletingAnUnknownIdSucceeds() {
        service.delete("missing").join();
    }

    @Test
    void deleteFailsWhenNotAcknowledged() {
        Punishment punishment = new Punishment("target", "mod", PunishmentType.KICK, "spam", null);
        store.seed(punishment).writesWill(WriteOutcome.UNACKNOWLEDGED);

        CompletionException thrown = assertThrows(CompletionException.class, () -> service.delete(punishment.getPunishmentId()).join());

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertTrue(service.find(punishment.getPunishmentId()).isPresent());
    }

    @Test
    void historyIsOnlyTheTargetsPunishments() {
        Punishment first = new Punishment("target", "mod", PunishmentType.KICK, "one", null);
        Punishment second = new Punishment("target", "other-mod", PunishmentType.BAN, "two", null);
        store.seed(first).seed(new Punishment("someone-else", "mod", PunishmentType.BAN, "x", null)).seed(second);

        assertEquals(List.of(first, second), service.history("target"));
        assertTrue(service.history("nobody").isEmpty());
    }
}
