package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.hypixel.nerdbot.app.channel.ForumTagTransfer;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagTransferCommandsTest {

    private static final String ADMIN = "7";
    private static final ForumTag OLD = tag("1", "Old");
    private static final ForumTag NEW = tag("2", "New");

    private final CallRecorder recorder = new CallRecorder();
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final List<ThreadChannel> tagged = new ArrayList<>();
    private ForumTagTransfer.Outcome outcome = new ForumTagTransfer.Outcome(0, 0);
    private final ForumChannel forum = Fakes.of(ForumChannel.class, Map.of(
        "getId", "50", "getName", "suggestions", "getAsMention", "<#50>",
        "getAvailableTags", List.of(OLD, NEW),
        "getAvailableTagById", (CallRecorder.Answer) args -> switch ((String) args[0]) {
            case "1" -> OLD;
            case "2" -> NEW;
            default -> null;
        }));
    private List<ForumChannel> forums = List.of(forum);
    private final Guild guild = Fakes.of(Guild.class, Map.of(
        "getForumChannels", (CallRecorder.Answer) args -> forums,
        "getForumChannelById", (CallRecorder.Answer) args -> "50".equals(args[0]) ? forum : null));

    private final ForumTagTransfer transfer = new ForumTagTransfer() {
        @Override
        public List<ThreadChannel> threadsWithTag(ForumChannel channel, ForumTag tag) {
            return tagged;
        }

        @Override
        public CompletableFuture<Outcome> transfer(List<ThreadChannel> threads, ForumTag from, ForumTag to, IntConsumer onProgress) {
            onProgress.accept(threads.size());
            return CompletableFuture.completedFuture(outcome);
        }
    };

    private final TagTransferCommands commands = new TagTransferCommands(transfer, logged::add, Runnable::run, new MutableClock());

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void thePanelListsForumsForTheAdmin() {
        commands.open(slash());

        assertTrue(DiscordFakes.lastEditText(recorder).contains("**Step 1:**"));
        StringSelectMenu select = DiscordFakes.selectMenu(DiscordFakes.lastComponents(recorder).getFirst());
        assertEquals("tag-channel-select-" + ADMIN, select.getId());
        assertEquals("50", select.getOptions().getFirst().getValue());
    }

    @Test
    void withoutForumsThereIsNothingToDo() {
        forums = List.of();

        commands.open(slash());

        assertEquals("❌ No forum channels found in this server!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void choosingAForumOffersItsTags() {
        commands.channelChosen(select("tag-channel-select-" + ADMIN, ADMIN, "50"));

        StringSelectMenu select = DiscordFakes.selectMenu(DiscordFakes.lastComponents(recorder).getFirst());
        assertEquals("tag-from-select-" + ADMIN + "-50", select.getId());
        assertEquals(List.of("1", "2"), select.getOptions().stream().map(option -> option.getValue()).toList());
    }

    @Test
    void someoneElsesPanelCantBeUsed() {
        commands.channelChosen(select("tag-channel-select-" + ADMIN, "99", "50"));

        assertEquals(TagTransferCommands.NOT_YOUR_PANEL, recorder.last("reply").arg(0));
        assertFalse(recorder.called("deferEdit"));
    }

    @Test
    void theDestinationListLeavesOutTheSourceTag() {
        commands.fromTagChosen(select("tag-from-select-" + ADMIN + "-50", ADMIN, "1"));

        StringSelectMenu select = DiscordFakes.selectMenu(DiscordFakes.lastComponents(recorder).getFirst());
        assertEquals("tag-to-select-" + ADMIN + "-50-1", select.getId());
        assertEquals(List.of("2"), select.getOptions().stream().map(option -> option.getValue()).toList());
    }

    @Test
    void theConfirmationCountsAffectedThreads() {
        tagged.addAll(List.of(Fakes.of(ThreadChannel.class, Map.of()), Fakes.of(ThreadChannel.class, Map.of())));

        commands.toTagChosen(select("tag-to-select-" + ADMIN + "-50-1", ADMIN, "2")).join();

        assertTrue(DiscordFakes.lastEditText(recorder).contains("📊 **Affected Threads:** 2"));
        List<String> ids = buttonIds();
        assertTrue(ids.contains("tag-confirm-execute-" + ADMIN + "-50-1-2"), ids.toString());
        assertTrue(ids.contains("tag-confirm-cancel-" + ADMIN + "-50-1-2"));
    }

    @Test
    void cancellingGoesBackToTheStart() {
        commands.confirm(button("tag-confirm-cancel-" + ADMIN + "-50-1-2", ADMIN)).join();

        assertTrue(DiscordFakes.lastEditText(recorder).contains("❌ Tag transfer cancelled."));
    }

    @Test
    void aCompleteTransferIsReportedAndLogged() {
        tagged.add(Fakes.of(ThreadChannel.class, Map.of()));
        outcome = new ForumTagTransfer.Outcome(1, 0);

        commands.confirm(button("tag-confirm-execute-" + ADMIN + "-50-1-2", ADMIN)).join();

        String result = DiscordFakes.lastEditText(recorder);
        assertTrue(result.startsWith("🏷️ **Tag Transfer Complete**"), result);
        assertTrue(result.contains("✅ **Moved:** 1 threads"));
        assertEquals("Forum Tag Transfer", logged.getFirst().getTitle());
    }

    @Test
    void failuresAreNotCalledASuccess() {
        tagged.addAll(List.of(Fakes.of(ThreadChannel.class, Map.of()), Fakes.of(ThreadChannel.class, Map.of())));
        outcome = new ForumTagTransfer.Outcome(1, 1);

        commands.confirm(button("tag-confirm-execute-" + ADMIN + "-50-1-2", ADMIN)).join();

        String result = DiscordFakes.lastEditText(recorder);
        assertTrue(result.startsWith("🏷️ **Tag Transfer Finished With Errors**"), result);
        assertTrue(result.contains("❌ **Failed:** 1 threads (see the logs)"));
    }

    @Test
    void noTaggedThreadsMeansNothingToMove() {
        commands.confirm(button("tag-confirm-execute-" + ADMIN + "-50-1-2", ADMIN)).join();

        assertTrue(DiscordFakes.lastEditText(recorder).contains("❌ No threads found with the source tag!"));
        assertTrue(logged.isEmpty());
    }

    @Test
    void aTagRemovedSinceTheConfirmationIsCaught() {
        commands.confirm(button("tag-confirm-execute-" + ADMIN + "-50-1-9", ADMIN)).join();

        assertTrue(DiscordFakes.lastEditText(recorder).contains("❌ Invalid channel or tags!"));
    }

    @Test
    void anOldOrBrokenButtonIsOutdated() {
        commands.confirm(button("tag-confirm-execute-" + ADMIN, ADMIN)).join();

        assertEquals("This panel is out of date. Run the command again.", recorder.last("reply").arg(0));
    }

    @Test
    void backReturnsToTheForumList() {
        commands.back(button("tag-back-" + ADMIN, ADMIN));

        assertTrue(DiscordFakes.lastEditText(recorder).contains("**Step 1:**"));
    }

    private List<String> buttonIds() {
        return DiscordFakes.lastComponents(recorder).stream()
            .flatMap(row -> row.getButtons().stream())
            .map(Button::getId)
            .toList();
    }

    private SlashCommandInteraction slash() {
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", DiscordFakes.user(ADMIN), "getGuild", guild,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private StringSelectInteraction select(String componentId, String userId, String value) {
        return recorder.fake(StringSelectInteraction.class, Map.of("getComponentId", componentId, "getUser", DiscordFakes.user(userId),
            "getValues", List.of(value), "getGuild", guild, "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private ButtonInteraction button(String componentId, String userId) {
        User user = Fakes.of(User.class, Map.of("getId", userId, "getAsMention", "<@" + userId + ">"));
        return recorder.fake(ButtonInteraction.class, Map.of("getComponentId", componentId, "getUser", user, "getGuild", guild,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private static ForumTag tag(String id, String name) {
        return Fakes.of(ForumTag.class, Map.of("getId", id, "getName", name));
    }
}
