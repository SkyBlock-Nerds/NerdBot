package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.hypixel.nerdbot.app.punishment.PunishmentService;
import net.hypixel.nerdbot.app.testsupport.FakePunishmentStore;
import net.hypixel.nerdbot.app.testsupport.FakePunishmentStore.WriteOutcome;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.Punishment;
import net.hypixel.nerdbot.marmalade.storage.database.model.punishment.PunishmentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PunishmentCommandsTest {

    private static final String MOD = "100";
    private static final String OTHER_MOD = "101";
    private static final String TARGET = "200";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private final CallRecorder recorder = new CallRecorder();
    private final FakePunishmentStore store = new FakePunishmentStore();
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final PunishmentCommands commands = new PunishmentCommands(
        new PunishmentService(store), logged::add, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Nested
    class Create {

        @Test
        void offersEveryPunishmentTypeOnAPanelOwnedByTheModerator() {
            commands.startCreate(slash(MOD), member(TARGET));

            assertDeferredEphemerally();
            assertEquals("Select the punishment type for <@" + TARGET + ">:", lastEditText());
            StringSelectMenu menu = selectMenu(componentsOfLastEdit().getFirst());
            assertEquals("punish-type-" + MOD + "-" + TARGET, menu.getId());
            assertEquals(Arrays.stream(PunishmentType.values()).map(Enum::name).toList(),
                menu.getOptions().stream().map(SelectOption::getValue).toList());
        }

        @Test
        void choosingATypeOpensTheReasonModal() {
            commands.chooseType(select(MOD, "punish-type-" + MOD + "-" + TARGET, "BAN"));

            Modal modal = (Modal) recorder.last("replyModal").arg(0);
            assertEquals("punish-modal-" + MOD + "-" + TARGET + "-BAN", modal.getId());
        }

        @Test
        void anotherModeratorCannotUseThePanel() {
            commands.chooseType(select(OTHER_MOD, "punish-type-" + MOD + "-" + TARGET, "BAN"));

            assertRepliedEphemerally(PunishmentCommands.NOT_YOUR_PANEL);
            assertFalse(recorder.called("replyModal"));
        }

        @Test
        void malformedPanelIdIsRejected() {
            commands.chooseType(select(MOD, "punish-type-" + MOD, "BAN"));

            assertRepliedEphemerally(PunishmentCommands.OUTDATED_PANEL);
            assertFalse(recorder.called("replyModal"));
        }

        @Test
        void submittingTheModalRecordsAndLogsThePunishment() {
            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-BAN", "spamming", "  "));

            Punishment saved = store.findByTargetUserId(TARGET).getFirst();
            assertEquals(MOD, saved.getModeratorUserId());
            assertEquals(PunishmentType.BAN, saved.getType());
            assertEquals("spamming", saved.getReason());
            assertNull(saved.getNotes());

            MessageEmbed reply = lastEditEmbeds().getFirst();
            assertEquals("Punishment Recorded", reply.getTitle());
            assertEquals(NOW, reply.getTimestamp().toInstant());
            assertFalse(fieldNames(reply).contains("Notes"));
            assertEquals(List.of(reply), logged);
        }

        @Test
        void notesAreShownWhenGiven() {
            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-WARNING", "spamming", "third time"));

            assertEquals("third time", field(lastEditEmbeds().getFirst(), "Notes"));
        }

        @Test
        void unacknowledgedSaveTellsTheModeratorAndLogsNothing() {
            store.writesWill(WriteOutcome.UNACKNOWLEDGED);

            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-BAN", "spamming", null));

            assertEquals("Failed to save punishment. Please try again.", lastEditText());
            assertTrue(logged.isEmpty());
        }

        @Test
        void failedSaveTellsTheModeratorAndLogsNothing() {
            store.writesWill(WriteOutcome.FAILED);

            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-BAN", "spamming", null));

            assertEquals("Failed to save punishment. Please try again.", lastEditText());
            assertTrue(logged.isEmpty());
        }

        @Test
        void unknownTypeInTheModalIdSavesNothing() {
            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-SMITE", "spamming", null));

            assertEquals(PunishmentCommands.OUTDATED_PANEL, lastEditText());
            assertTrue(store.getAll().isEmpty());
        }

        @Test
        void blankReasonSavesNothing() {
            commands.submit(modal(MOD, "punish-modal-" + MOD + "-" + TARGET + "-BAN", "   ", null));

            assertEquals("A reason is required.", lastEditText());
            assertTrue(store.getAll().isEmpty());
        }

        @Test
        void anotherModeratorCannotSubmitTheModal() {
            commands.submit(modal(OTHER_MOD, "punish-modal-" + MOD + "-" + TARGET + "-BAN", "spamming", null));

            assertRepliedEphemerally(PunishmentCommands.NOT_YOUR_PANEL);
            assertTrue(store.getAll().isEmpty());
        }
    }

    @Nested
    class Search {

        @Test
        void unknownTypeIsReportedInsteadOfBeingIgnored() {
            commands.search(slash(MOD), member(MOD), "smite");

            assertEquals("Unknown punishment type: smite", lastEditText());
        }

        @Test
        void needsAtLeastOneFilter() {
            commands.search(slash(MOD), null, null);

            assertEquals("Please provide at least one filter (moderator or type).", lastEditText());
        }

        @Test
        void saysSoWhenNothingMatches() {
            commands.search(slash(MOD), null, "kick");

            assertEquals("No punishments found matching the given filters.", lastEditText());
        }

        @Test
        void listsEachMatchAsAField() {
            store.seed(new Punishment(TARGET, MOD, PunishmentType.BAN, "spamming", null))
                .seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.BAN, "griefing", null))
                .seed(new Punishment(TARGET, MOD, PunishmentType.KICK, "rude", null));

            commands.search(slash(MOD), member(MOD), "ban");

            MessageEmbed embed = lastEditEmbeds().getFirst();
            assertEquals("Showing 1 result(s)", embed.getFooter().getText());
            assertTrue(embed.getFields().getFirst().getValue().contains("**Reason:** spamming"));
        }
    }

    @Nested
    class Stats {

        @Test
        void showsTotalAndPerTypeCounts() {
            store.seed(new Punishment(TARGET, MOD, PunishmentType.BAN, "a", null))
                .seed(new Punishment(TARGET, MOD, PunishmentType.WARNING, "b", null))
                .seed(new Punishment(TARGET, MOD, PunishmentType.WARNING, "c", null));

            commands.showStats(slash(MOD), member(TARGET));

            MessageEmbed embed = lastEditEmbeds().getFirst();
            assertEquals("Punishment Stats for Member " + TARGET, embed.getTitle());
            assertEquals("3", field(embed, "Total"));
            assertEquals("2", field(embed, "Warning"));
            assertEquals("1", field(embed, "Ban"));
            assertNull(embed.getDescription());
        }

        @Test
        void saysSoWhenThereAreNone() {
            commands.showStats(slash(MOD), member(TARGET));

            MessageEmbed embed = lastEditEmbeds().getFirst();
            assertEquals("0", field(embed, "Total"));
            assertEquals("This user has no recorded punishments.", embed.getDescription());
        }
    }

    @Nested
    class History {

        @Test
        void emptyHistorySaysSoWithoutComponents() {
            commands.showHistory(slash(MOD), member(TARGET));

            assertDeferredEphemerally();
            assertTrue(lastEditText().endsWith("No punishments found for this user."));
            assertTrue(componentsOfLastEdit().isEmpty());
        }

        @Test
        void singlePageHasNoNavigation() {
            seedHistory(3);

            commands.showHistory(slash(MOD), member(TARGET));

            assertTrue(lastEditText().contains("**Total:** 3 punishment(s)\n"));
            List<ActionRow> rows = componentsOfLastEdit();
            assertEquals(1, rows.size());
            StringSelectMenu menu = selectMenu(rows.getFirst());
            assertEquals("punish-detail-" + MOD + "-" + TARGET, menu.getId());
            assertEquals(List.of("#1 - Warning", "#2 - Warning", "#3 - Warning"),
                menu.getOptions().stream().map(SelectOption::getLabel).toList());
        }

        @Test
        void firstPageCanOnlyGoForward() {
            seedHistory(25);

            commands.showHistory(slash(MOD), member(TARGET));

            assertTrue(lastEditText().contains("(Page 1/3)"));
            assertEquals(PunishmentCommands.ITEMS_PER_PAGE, selectMenu(componentsOfLastEdit().getFirst()).getOptions().size());
            List<Button> buttons = componentsOfLastEdit().get(1).getButtons();
            assertTrue(buttons.get(0).isDisabled());
            assertFalse(buttons.get(1).isDisabled());
            assertEquals("punish-nav-next-1-" + MOD + "-" + TARGET, buttons.get(1).getId());
        }

        @Test
        void nextMovesOnAPage() {
            seedHistory(25);

            commands.navigate(button(MOD, "punish-nav-next-1-" + MOD + "-" + TARGET));

            assertTrue(recorder.called("deferEdit"));
            assertTrue(lastEditText().contains("(Page 2/3)"));
            assertEquals("#11 - Warning", selectMenu(componentsOfLastEdit().getFirst()).getOptions().getFirst().getLabel());
        }

        @Test
        void lastPageShowsTheRemainderAndCannotGoForward() {
            seedHistory(25);

            commands.navigate(button(MOD, "punish-nav-next-2-" + MOD + "-" + TARGET));

            assertTrue(lastEditText().contains("(Page 3/3)"));
            assertEquals(5, selectMenu(componentsOfLastEdit().getFirst()).getOptions().size());
            assertTrue(componentsOfLastEdit().get(1).getButtons().get(1).isDisabled());
        }

        @Test
        void pagesOutsideTheHistoryAreClamped() {
            seedHistory(25);

            commands.navigate(button(MOD, "punish-nav-next-3-" + MOD + "-" + TARGET));
            assertTrue(lastEditText().contains("(Page 3/3)"));

            commands.navigate(button(MOD, "punish-nav-prev-1-" + MOD + "-" + TARGET));
            assertTrue(lastEditText().contains("(Page 1/3)"));
        }

        @Test
        void historyThatShrankSinceThePanelOpenedShowsItsLastPage() {
            seedHistory(5);

            commands.navigate(button(MOD, "punish-nav-next-2-" + MOD + "-" + TARGET));

            assertTrue(lastEditText().contains("**Total:** 5 punishment(s)\n"));
            assertEquals(5, selectMenu(componentsOfLastEdit().getFirst()).getOptions().size());
        }

        @Test
        void navigationChecksTheModeratorInTheThirdPart() {
            seedHistory(25);

            commands.navigate(button(OTHER_MOD, "punish-nav-next-1-" + MOD + "-" + TARGET));

            assertRepliedEphemerally(PunishmentCommands.NOT_YOUR_PANEL);
            assertFalse(recorder.called("editOriginal"));
        }

        @Test
        void nonNumericPageIsRejected() {
            commands.navigate(button(MOD, "punish-nav-next-two-" + MOD + "-" + TARGET));

            assertRepliedEphemerally(PunishmentCommands.OUTDATED_PANEL);
            assertFalse(recorder.called("editOriginal"));
        }

        @Test
        void backReturnsToTheFirstPage() {
            seedHistory(25);

            commands.back(button(MOD, "punish-back-" + MOD + "-" + TARGET));

            assertTrue(lastEditText().contains("(Page 1/3)"));
        }
    }

    @Nested
    class Detail {

        @Test
        void showsThePunishmentWithDeleteAndBackButtons() {
            Punishment punishment = seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.MUTE, "loud", "in vc"));

            commands.selectDetail(select(MOD, "punish-detail-" + MOD + "-" + TARGET, punishment.getPunishmentId()));

            MessageEmbed embed = lastEmbedSet();
            assertEquals("Punishment Detail", embed.getTitle());
            assertEquals("<@" + OTHER_MOD + ">", field(embed, "Moderator"));
            assertEquals("in vc", field(embed, "Notes"));
            assertEquals("ID: " + punishment.getPunishmentId(), embed.getFooter().getText());
            List<Button> buttons = componentsOfLastEdit().getFirst().getButtons();
            assertEquals("punish-delete-" + MOD + "-" + punishment.getPunishmentId(), buttons.get(0).getId());
            assertEquals("punish-back-" + MOD + "-" + TARGET, buttons.get(1).getId());
        }

        @Test
        void missingPunishmentFallsBackToTheHistory() {
            seedHistory(1);

            commands.selectDetail(select(MOD, "punish-detail-" + MOD + "-" + TARGET, "gone"));

            assertTrue(lastEditText().contains("Punishment not found! It may have been deleted."));
            assertTrue(lastEditText().contains("**Total:** 1 punishment(s)"));
        }
    }

    @Nested
    class Delete {

        @Test
        void askingToDeleteShowsConfirmAndCancel() {
            commands.askToDelete(button(MOD, "punish-delete-" + MOD + "-some-uuid"));

            List<Button> buttons = componentsOfLastEdit().getFirst().getButtons();
            assertEquals("punish-confirm-yes-" + MOD + "-some-uuid", buttons.get(0).getId());
            assertEquals("punish-confirm-no-" + MOD + "-some-uuid", buttons.get(1).getId());
        }

        @Test
        void confirmingDeletesLogsAndReturnsToTheHistory() {
            Punishment punishment = seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.KICK, "rude", null));

            commands.confirmDelete(button(MOD, "punish-confirm-yes-" + MOD + "-" + punishment.getPunishmentId()));

            assertTrue(store.findById(punishment.getPunishmentId()).isEmpty());
            assertEquals(1, logged.size());
            assertEquals("Punishment Deleted", logged.getFirst().getTitle());
            assertEquals("<@" + MOD + ">", field(logged.getFirst(), "Deleted By"));
            assertEquals(NOW, logged.getFirst().getTimestamp().toInstant());
            assertTrue(lastEditText().contains("Successfully deleted punishment record."));
            assertTrue(lastEditText().endsWith("No punishments found for this user."));
        }

        @Test
        void confirmingAnAlreadyDeletedPunishmentSaysSo() {
            commands.confirmDelete(button(MOD, "punish-confirm-yes-" + MOD + "-gone"));

            assertEquals("Punishment not found! It may have already been deleted.", lastEditText());
            assertTrue(logged.isEmpty());
        }

        @Test
        void failedDeleteKeepsThePunishmentAndLogsNothing() {
            Punishment punishment = seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.KICK, "rude", null));
            store.writesWill(WriteOutcome.FAILED);

            commands.confirmDelete(button(MOD, "punish-confirm-yes-" + MOD + "-" + punishment.getPunishmentId()));

            assertEquals("Failed to delete punishment. Please try again.", lastEditText());
            assertTrue(store.findById(punishment.getPunishmentId()).isPresent());
            assertTrue(logged.isEmpty());
        }

        @Test
        void anotherModeratorCannotConfirm() {
            Punishment punishment = seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.KICK, "rude", null));

            commands.confirmDelete(button(OTHER_MOD, "punish-confirm-yes-" + MOD + "-" + punishment.getPunishmentId()));

            assertRepliedEphemerally(PunishmentCommands.NOT_YOUR_PANEL);
            assertTrue(store.findById(punishment.getPunishmentId()).isPresent());
        }

        @Test
        void cancellingGoesBackToTheDetail() {
            Punishment punishment = seed(new Punishment(TARGET, OTHER_MOD, PunishmentType.KICK, "rude", null));

            commands.cancelDelete(button(MOD, "punish-confirm-no-" + MOD + "-" + punishment.getPunishmentId()));

            assertEquals("Punishment Detail", lastEmbedSet().getTitle());
            assertTrue(store.findById(punishment.getPunishmentId()).isPresent());
        }

        @Test
        void cancellingAfterSomeoneElseDeletedItSaysSo() {
            commands.cancelDelete(button(MOD, "punish-confirm-no-" + MOD + "-gone"));

            assertEquals("Punishment not found! It may have been deleted.", lastEditText());
        }
    }

    private Punishment seed(Punishment punishment) {
        store.seed(punishment);
        return punishment;
    }

    private void seedHistory(int count) {
        IntStream.range(0, count).forEach(i -> store.seed(new Punishment(TARGET, MOD, PunishmentType.WARNING, "reason " + i, null)));
    }

    private static User user(String id) {
        return Fakes.of(User.class, Map.of("getId", id));
    }

    private static Member member(String id) {
        return Fakes.of(Member.class, Map.of(
            "getId", id,
            "getEffectiveName", "Member " + id,
            "getEffectiveAvatarUrl", "https://cdn.discordapp.com/embed/avatars/0.png"));
    }

    private SlashCommandInteraction slash(String userId) {
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", user(userId)));
    }

    private ButtonInteraction button(String userId, String componentId) {
        return recorder.fake(ButtonInteraction.class, Map.of("getUser", user(userId), "getComponentId", componentId));
    }

    private StringSelectInteraction select(String userId, String componentId, String value) {
        return recorder.fake(StringSelectInteraction.class, Map.of("getUser", user(userId), "getComponentId", componentId, "getValues", List.of(value)));
    }

    private ModalInteraction modal(String userId, String modalId, String reason, String notes) {
        List<ModalMapping> values = new ArrayList<>();
        values.add(modalValue("reason", reason));
        if (notes != null) {
            values.add(modalValue("notes", notes));
        }

        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", user(userId));
        answers.put("getModalId", modalId);
        answers.put("getValues", values);
        return recorder.fake(ModalInteraction.class, answers);
    }

    private static ModalMapping modalValue(String id, String value) {
        return new ModalMapping(DataObject.empty().put("custom_id", id).put("value", value).put("type", 4));
    }

    private void assertDeferredEphemerally() {
        assertTrue(recorder.called("deferReply"));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    private void assertRepliedEphemerally(String message) {
        assertEquals(message, recorder.last("reply").arg(0));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    private String lastEditText() {
        return (String) recorder.last("editOriginal").arg(0);
    }

    private List<MessageEmbed> lastEditEmbeds() {
        return lastCallItems("editOriginalEmbeds", MessageEmbed.class);
    }

    private MessageEmbed lastEmbedSet() {
        return lastCallItems("setEmbeds", MessageEmbed.class).getFirst();
    }

    private List<ActionRow> componentsOfLastEdit() {
        return lastCallItems("setComponents", ActionRow.class);
    }

    /**
     * The items passed to the last call of {@code method}, whether as varargs or as one collection;
     * JDA's varargs overloads hand on to the collection ones.
     */
    private <T> List<T> lastCallItems(String method, Class<T> type) {
        List<Object> args = recorder.last(method).args();
        if (args.size() == 1 && args.getFirst() instanceof Collection<?> items) {
            return items.stream().map(type::cast).toList();
        }
        return args.stream().map(type::cast).toList();
    }

    private static StringSelectMenu selectMenu(ActionRow row) {
        return (StringSelectMenu) row.getActionComponents().getFirst();
    }

    private static List<String> fieldNames(MessageEmbed embed) {
        return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
    }

    private static String field(MessageEmbed embed, String name) {
        return embed.getFields().stream()
            .filter(field -> name.equals(field.getName()))
            .findFirst()
            .map(MessageEmbed.Field::getValue)
            .orElseThrow(() -> new AssertionError("No field " + name + " in " + fieldNames(embed)));
    }
}
