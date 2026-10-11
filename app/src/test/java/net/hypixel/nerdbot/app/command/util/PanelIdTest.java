package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelIdTest {

    private static final String UUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final PanelId TRAILING_UUID = PanelId.of("thing-delete", 2, 0);
    private static final PanelId MIDDLE_UUID = new PanelId("thing-detail", 3, 2, 1);

    @Test
    void buildJoinsThePartsAfterThePrefix() {
        assertEquals("thing-delete-111-" + UUID, TRAILING_UUID.build("111", UUID));
        assertEquals("thing-detail-back-" + UUID + "-111", MIDDLE_UUID.build("back", UUID, "111"));
    }

    @Test
    void buildRejectsTheWrongNumberOfParts() {
        assertThrows(IllegalArgumentException.class, () -> TRAILING_UUID.build("111"));
        assertThrows(IllegalArgumentException.class, () -> TRAILING_UUID.build("111", UUID, "extra"));
    }

    @Test
    void parseReversesBuildWithTheDashedPartLast() {
        assertEquals(List.of("111", UUID), TRAILING_UUID.parse(TRAILING_UUID.build("111", UUID)).orElseThrow());
    }

    @Test
    void parseReversesBuildWithTheDashedPartInTheMiddle() {
        assertEquals(List.of("delete", UUID, "111"), MIDDLE_UUID.parse(MIDDLE_UUID.build("delete", UUID, "111")).orElseThrow());
    }

    @Test
    void aPartWithoutDashesStillParses() {
        assertEquals(List.of("back", "plain", "111"), MIDDLE_UUID.parse("thing-detail-back-plain-111").orElseThrow());
    }

    @Test
    void aLongerPrefixDoesNotMatch() {
        PanelId confirm = PanelId.of("thing-confirm", 2, 0);

        assertTrue(confirm.parse("thing-confirm-yes-111-" + UUID).isPresent(), "the shorter prefix sees 'yes' as a part");
        assertTrue(PanelId.of("thing-confirm-yes", 2, 0).parse("thing-confirm-no-111-" + UUID).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "thing-delete",
        "thing-delete-",
        "thing-delete-111",
        "thing-delete-111-",
        "thing-delete--" + UUID,
        "thing-delete-111-" + UUID + "-",
        "thing-deletes-111-" + UUID,
        "other-111-" + UUID
    })
    void malformedIdsAreRejected(String id) {
        assertTrue(TRAILING_UUID.parse(id).isEmpty());
    }

    @Test
    void shapesMustBeValid() {
        assertThrows(IllegalArgumentException.class, () -> new PanelId("x", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PanelId("x", 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new PanelId("x", 2, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new PanelId("x", 2, -1, 0));
        assertThrows(NullPointerException.class, () -> new PanelId(null, 1, 0, 0));
    }

    @Test
    void parseForReturnsThePartsToTheOwner() {
        CallRecorder recorder = new CallRecorder();

        assertEquals(List.of("delete", UUID, "111"),
            MIDDLE_UUID.parseFor(button(recorder, "111", MIDDLE_UUID.build("delete", UUID, "111")), MIDDLE_UUID.build("delete", UUID, "111"), "not yours").orElseThrow());
        assertFalse(recorder.called("reply"));
    }

    @Test
    void parseForTellsSomeoneElseItIsNotTheirs() {
        CallRecorder recorder = new CallRecorder();
        String id = MIDDLE_UUID.build("delete", UUID, "111");

        assertTrue(MIDDLE_UUID.parseFor(button(recorder, "222", id), id, "not yours").isEmpty());
        assertEquals("not yours", recorder.last("reply").arg(0));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    @Test
    void parseForSaysAMalformedIdIsOutdated() {
        CallRecorder recorder = new CallRecorder();

        assertTrue(MIDDLE_UUID.parseFor(button(recorder, "111", "thing-detail-delete"), "thing-detail-delete", "not yours").isEmpty());
        assertEquals(PanelId.OUTDATED, recorder.last("reply").arg(0));
    }

    private static ButtonInteraction button(CallRecorder recorder, String userId, String componentId) {
        return recorder.fake(ButtonInteraction.class, Map.of(
            "getUser", Fakes.of(User.class, Map.of("getId", userId)),
            "getComponentId", componentId));
    }
}
