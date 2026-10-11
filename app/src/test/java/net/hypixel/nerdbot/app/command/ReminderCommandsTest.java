package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.reminder.ReminderScheduler;
import net.hypixel.nerdbot.app.reminder.ReminderService;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeReminderStore;
import net.hypixel.nerdbot.app.testsupport.RecordingReminderSender;
import net.hypixel.nerdbot.app.testsupport.WriteOutcome;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.reminder.Reminder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static net.hypixel.nerdbot.app.testsupport.DiscordFakes.modalValue;
import static net.hypixel.nerdbot.app.testsupport.DiscordFakes.selectMenu;
import static net.hypixel.nerdbot.app.testsupport.DiscordFakes.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderCommandsTest {

    private static final String USER = "100";
    private static final String OTHER_USER = "101";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private final CallRecorder recorder = new CallRecorder();
    private final FakeReminderStore store = new FakeReminderStore();
    private final RecordingReminderSender sender = new RecordingReminderSender();
    private final ManualScheduledExecutor executor = new ManualScheduledExecutor();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ReminderCommands commands = new ReminderCommands(
        new ReminderService(store, new ReminderScheduler(executor, store, sender, clock), sender, clock));

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void idsKeepTheFormatOfButtonsAlreadyPosted() {
        String uuid = "0f8fad5b-d9cb-469f-a165-70867728950e";

        assertEquals("reminder-nav-next-2-100", ReminderCommands.NAV.build("next", 2, "100"));
        assertEquals("reminder-manage-100", ReminderCommands.MANAGE.build("100"));
        assertEquals("reminder-detail-delete-" + uuid + "-100", ReminderCommands.DETAIL.build("delete", uuid, "100"));
        assertEquals("reminder-create-100", ReminderCommands.CREATE.build("100"));
        assertEquals("reminder-modal-100", ReminderCommands.MODAL.build("100"));
        assertEquals(List.of("delete", uuid, "100"), ReminderCommands.DETAIL.parse("reminder-detail-delete-" + uuid + "-100").orElseThrow());
    }

    @Nested
    class Panel {

        @Test
        void emptyPanelOffersToCreateOne() {
            commands.showPanel(slash(USER));

            assertTrue(recorder.called("deferReply"));
            assertTrue(lastEditText().contains("You don't have any reminders set!"));
            List<ActionRow> rows = componentsOfLastEdit();
            assertEquals(1, rows.size());
            assertEquals("reminder-create-" + USER, rows.getFirst().getButtons().getFirst().getId());
        }

        @Test
        void onlyTheUsersOwnRemindersAreListedSoonestFirst() {
            seed(USER, Duration.ofDays(2), "later");
            seed(USER, Duration.ofHours(1), "sooner");
            seed(OTHER_USER, Duration.ofMinutes(5), "not mine");

            commands.showPanel(slash(USER));

            assertTrue(lastEditText().contains("**Total:** 2 reminders\n"));
            StringSelectMenu menu = selectMenu(componentsOfLastEdit().getFirst());
            assertEquals("reminder-manage-" + USER, menu.getId());
            assertEquals(List.of("sooner", "later"), menu.getOptions().stream().map(option -> option.getDescription()).toList());
        }

        @Test
        void morePagesThanOneCanBeStepped() {
            seedMany(30);

            commands.showPanel(slash(USER));

            assertTrue(lastEditText().contains("(Page 1/2)"));
            assertEquals(ReminderCommands.REMINDERS_PER_PAGE, selectMenu(componentsOfLastEdit().getFirst()).getOptions().size());
            List<Button> buttons = componentsOfLastEdit().get(1).getButtons();
            assertTrue(buttons.get(0).isDisabled());
            assertEquals("reminder-nav-next-1-" + USER, buttons.get(1).getId());
            assertEquals("reminder-create-" + USER, buttons.get(2).getId());

            commands.navigate(button(USER, "reminder-nav-next-1-" + USER));

            assertTrue(lastEditText().contains("(Page 2/2)"));
            assertEquals(5, selectMenu(componentsOfLastEdit().getFirst()).getOptions().size());
            assertEquals("#26 - Oct 11, 2026 at 12:26 PM UTC", selectMenu(componentsOfLastEdit().getFirst()).getOptions().getFirst().getLabel());
        }

        @Test
        void pagesPastTheEndAreClamped() {
            seedMany(30);

            commands.navigate(button(USER, "reminder-nav-next-5-" + USER));

            assertTrue(lastEditText().contains("(Page 2/2)"));
        }

        @Test
        void someoneElseCannotPage() {
            commands.navigate(button(OTHER_USER, "reminder-nav-next-1-" + USER));

            assertEquals(ReminderCommands.NOT_YOUR_PANEL, recorder.last("reply").arg(0));
            assertFalse(recorder.called("editOriginal"));
        }

        @Test
        void nonNumericPageIsOutdated() {
            commands.navigate(button(USER, "reminder-nav-next-two-" + USER));

            assertEquals(PanelId.OUTDATED, recorder.last("reply").arg(0));
        }
    }

    @Nested
    class Detail {

        @Test
        void selectingAReminderShowsItWithDeleteAndBack() {
            Reminder reminder = seed(USER, Duration.ofHours(1), "water the plants");

            commands.selectReminder(select(USER, "reminder-manage-" + USER, reminder.getUuid().toString()));

            assertTrue(lastEditText().contains("water the plants"));
            assertTrue(lastEditText().contains("<#channel>"));
            List<Button> buttons = componentsOfLastEdit().getFirst().getButtons();
            assertEquals("reminder-detail-delete-" + reminder.getUuid() + "-" + USER, buttons.get(0).getId());
            assertEquals("reminder-detail-back-" + reminder.getUuid() + "-" + USER, buttons.get(1).getId());
        }

        @Test
        void aReminderThatIsGoneShowsTheListWithANote() {
            commands.selectReminder(select(USER, "reminder-manage-" + USER, "0f8fad5b-d9cb-469f-a165-70867728950e"));

            assertTrue(lastEditText().contains("❌ Reminder not found!"));
        }

        @Test
        void deletingRemovesItAndShowsTheList() {
            Reminder reminder = seed(USER, Duration.ofHours(1), "water the plants");

            commands.detailAction(button(USER, "reminder-detail-delete-" + reminder.getUuid() + "-" + USER));

            assertTrue(lastEditText().contains("✅ Successfully deleted reminder!"));
            assertFalse(store.contains(reminder.getUuid()));
        }

        @Test
        void aPanelOpenedForSomeoneElsesReminderCannotDeleteIt() {
            Reminder reminder = seed(USER, Duration.ofHours(1), "water the plants");

            commands.detailAction(button(OTHER_USER, "reminder-detail-delete-" + reminder.getUuid() + "-" + OTHER_USER));

            assertTrue(lastEditText().contains("❌ Reminder not found!"));
            assertTrue(store.contains(reminder.getUuid()));
        }

        @Test
        void someoneElseCannotUseTheButtons() {
            Reminder reminder = seed(USER, Duration.ofHours(1), "water the plants");

            commands.detailAction(button(OTHER_USER, "reminder-detail-delete-" + reminder.getUuid() + "-" + USER));

            assertEquals(ReminderCommands.NOT_YOUR_PANEL, recorder.last("reply").arg(0));
            assertTrue(store.contains(reminder.getUuid()));
        }

        @Test
        void backShowsTheList() {
            Reminder reminder = seed(USER, Duration.ofHours(1), "water the plants");

            commands.detailAction(button(USER, "reminder-detail-back-" + reminder.getUuid() + "-" + USER));

            assertTrue(lastEditText().contains("**Total:** 1 reminders"));
            assertTrue(store.contains(reminder.getUuid()));
        }
    }

    @Nested
    class Create {

        @Test
        void theCreateButtonOpensTheModal() {
            commands.createButton(button(USER, "reminder-create-" + USER));

            assertEquals("reminder-modal-" + USER, ((Modal) recorder.last("replyModal").arg(0)).getId());
        }

        @Test
        void someoneElseCannotUseTheCreateButton() {
            commands.createButton(button(OTHER_USER, "reminder-create-" + USER));

            assertEquals(ReminderCommands.NOT_YOUR_PANEL, recorder.last("reply").arg(0));
            assertFalse(recorder.called("replyModal"));
        }

        @Test
        void submittingCreatesTheReminder() {
            commands.submit(modal(USER, "2h", "water the plants", textChannel(true, true)));

            assertTrue(lastEditText().startsWith("✅ Successfully created reminder for"));
            assertEquals(1, store.size());
            assertEquals(1, sender.confirmed.size());
            assertEquals(List.of(Duration.ofHours(2)), executor.pendingDelays());
        }

        @Test
        void badInputShowsTheReason() {
            commands.submit(modal(USER, "0s", "water the plants", textChannel(true, true)));

            assertEquals("❌ You cannot set a reminder in the past!", lastEditText());
            assertEquals(0, store.size());
        }

        @Test
        void failedSaveShowsAnError() {
            store.savesWill(WriteOutcome.FAILED);

            commands.submit(modal(USER, "2h", "water the plants", textChannel(true, true)));

            assertEquals("❌ An error occurred while saving the reminder! Please try again.", lastEditText());
        }

        @Test
        void missingFieldsAreReported() {
            commands.submit(modal(USER, null, "water the plants", textChannel(true, true)));

            assertEquals("❌ Missing required fields! Please fill out all fields.", lastEditText());
        }

        @Test
        void aChannelTheBotCannotPostInIsRefused() {
            commands.submit(modal(USER, "2h", "water the plants", textChannel(false, true)));

            assertEquals("❌ I don't have permission to send messages in this channel!", lastEditText());
            assertEquals(0, store.size());
        }

        @Test
        void aThreadNeedsThreadPermissionToo() {
            commands.submit(modal(USER, "2h", "water the plants", channel(ChannelType.GUILD_PUBLIC_THREAD, true, false)));

            assertEquals("❌ I don't have permission to send messages in threads!", lastEditText());
        }

        @Test
        void aThreadWithPermissionIsFine() {
            commands.submit(modal(USER, "2h", "water the plants", channel(ChannelType.GUILD_PUBLIC_THREAD, true, true)));

            assertEquals(1, store.size());
        }

        @Test
        void outsideAServerIsRefused() {
            ModalInteraction interaction = modal(USER, "2h", "water the plants", null);

            commands.submit(interaction);

            assertEquals("❌ Reminders can only be created from a server channel!", lastEditText());
        }

        @Test
        void someoneElseCannotSubmitTheModal() {
            commands.submit(modal(OTHER_USER, USER, "2h", "water the plants", textChannel(true, true)));

            assertEquals(ReminderCommands.NOT_YOUR_PANEL, recorder.last("reply").arg(0));
            assertEquals(0, store.size());
        }
    }

    private Reminder seed(String userId, Duration fromNow, String description) {
        Reminder reminder = new Reminder(description, NOW.plus(fromNow).toEpochMilli(), "channel", userId);
        store.seed(reminder);
        return reminder;
    }

    private void seedMany(int count) {
        IntStream.range(0, count).forEach(i -> seed(USER, Duration.ofMinutes(i + 1), "reminder " + i));
    }

    private SlashCommandInteraction slash(String userId) {
        return DiscordFakes.slash(recorder, userId);
    }

    private ButtonInteraction button(String userId, String componentId) {
        return DiscordFakes.button(recorder, userId, componentId);
    }

    private StringSelectInteraction select(String userId, String componentId, String value) {
        return DiscordFakes.select(recorder, userId, componentId, value);
    }

    /** Where the modal was submitted from; null for a guild means outside a server. */
    private record Place(Guild guild, MessageChannelUnion channel) {
    }

    private Place textChannel(boolean canSend, boolean canSendInThreads) {
        return channel(ChannelType.TEXT, canSend, canSendInThreads);
    }

    private Place channel(ChannelType type, boolean canSend, boolean canSendInThreads) {
        Member permittedSelf = recorder.fake(Member.class, Map.of("hasPermission", (CallRecorder.Answer) args -> {
            List<Object> permissions = Arrays.asList((Object[]) args[args.length - 1]);
            if (permissions.contains(Permission.MESSAGE_SEND_IN_THREADS)) {
                return canSendInThreads;
            }
            return canSend;
        }));
        Guild guild = Fakes.of(Guild.class, Map.of("getSelfMember", permittedSelf));
        MessageChannelUnion union = Fakes.of(MessageChannelUnion.class, Map.of(
            "getType", type,
            "getId", "300",
            "asGuildMessageChannel", Fakes.of(GuildMessageChannel.class, Map.of())));
        return new Place(guild, union);
    }

    private ModalInteraction modal(String userId, String time, String description, Place place) {
        return modal(userId, userId, time, description, place);
    }

    private ModalInteraction modal(String clickingUserId, String ownerId, String time, String description, Place place) {
        List<ModalMapping> values = new ArrayList<>();
        if (time != null) {
            values.add(modalValue("time", time));
        }
        values.add(modalValue("description", description));

        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", user(clickingUserId));
        answers.put("getModalId", "reminder-modal-" + ownerId);
        answers.put("getValues", values);
        answers.put("getGuild", place == null ? null : place.guild());
        answers.put("getChannel", place == null ? null : place.channel());
        return recorder.fake(ModalInteraction.class, answers);
    }

    private String lastEditText() {
        return DiscordFakes.lastEditText(recorder);
    }

    private List<ActionRow> componentsOfLastEdit() {
        return DiscordFakes.lastComponents(recorder);
    }
}
