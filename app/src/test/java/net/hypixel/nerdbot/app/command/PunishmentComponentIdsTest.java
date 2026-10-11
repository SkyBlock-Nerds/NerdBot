package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentComponentIdsTest {

    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";

    @Test
    void idsKeepTheFormatOfButtonsAlreadyPosted() {
        assertEquals("punish-type-1-2", PunishmentComponentIds.build(PunishmentComponentIds.TYPE, "1", "2"));
        assertEquals("punish-modal-1-2-BAN", PunishmentComponentIds.build(PunishmentComponentIds.MODAL, "1", "2", "BAN"));
        assertEquals("punish-detail-1-2", PunishmentComponentIds.build(PunishmentComponentIds.DETAIL, "1", "2"));
        assertEquals("punish-nav-next-3-1-2", PunishmentComponentIds.build(PunishmentComponentIds.NAV, "next", 3, "1", "2"));
        assertEquals("punish-delete-1-" + UUID, PunishmentComponentIds.build(PunishmentComponentIds.DELETE, "1", UUID));
        assertEquals("punish-confirm-yes-1-" + UUID, PunishmentComponentIds.build(PunishmentComponentIds.CONFIRM_YES, "1", UUID));
        assertEquals("punish-confirm-no-1-" + UUID, PunishmentComponentIds.build(PunishmentComponentIds.CONFIRM_NO, "1", UUID));
        assertEquals("punish-back-1-2", PunishmentComponentIds.build(PunishmentComponentIds.BACK, "1", "2"));
    }

    @Test
    void parseReversesBuild() {
        String id = PunishmentComponentIds.build(PunishmentComponentIds.NAV, "prev", 2, "111", "222");

        assertEquals(List.of("prev", "2", "111", "222"), PunishmentComponentIds.parse(PunishmentComponentIds.NAV, id, 4).orElseThrow());
    }

    @Test
    void lastPartKeepsTheDashesInAPunishmentId() {
        String id = "punish-confirm-yes-111-" + UUID;

        assertEquals(List.of("111", UUID), PunishmentComponentIds.parse(PunishmentComponentIds.CONFIRM_YES, id, 2).orElseThrow());
    }

    @Test
    void confirmPrefixesDoNotMatchEachOther() {
        String cancel = PunishmentComponentIds.build(PunishmentComponentIds.CONFIRM_NO, "111", UUID);

        assertTrue(PunishmentComponentIds.parse(PunishmentComponentIds.CONFIRM_YES, cancel, 2).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "punish-type",
        "punish-type-",
        "punish-type-111",
        "punish-type-111-",
        "punish-type--222",
        "punish-typo-111-222",
        "punish-types-111-222",
        "other-111-222"
    })
    void malformedIdsAreRejected(String id) {
        assertTrue(PunishmentComponentIds.parse(PunishmentComponentIds.TYPE, id, 2).isEmpty());
    }
}
