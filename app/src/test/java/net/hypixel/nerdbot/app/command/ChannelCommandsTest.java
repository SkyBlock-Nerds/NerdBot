package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.config.objects.CustomForumTag;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCommandsTest {

    private static final String SUGGESTIONS_FORUM = "500";
    private static final String OTHER_FORUM = "501";
    private static final String MODERATOR = "100";

    private static final ForumTag REVIEWED = tag("Reviewed");
    private static final ForumTag OPEN = tag("Open");
    private static final ForumTag BUG = tag("Bug");
    private static final ForumTag MINE = tag("Callum's Pick");

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final ChannelCommands commands = new ChannelCommands(() -> config, Runnable::run);
    private boolean managerSucceeds = true;

    @BeforeEach
    void setUp() {
        config.getSuggestionConfig().setForumChannelId(SUGGESTIONS_FORUM);
        config.getSuggestionConfig().setReviewedTag("Reviewed");
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void tagsAfterLockAddsTheTagOnce() {
        assertEquals(Optional.of(List.of(BUG, REVIEWED)), ChannelCommands.tagsAfterLock(List.of(BUG), REVIEWED, null, false));
        assertEquals(Optional.of(List.of(BUG, REVIEWED)), ChannelCommands.tagsAfterLock(List.of(BUG, REVIEWED), REVIEWED, null, false));
    }

    @Test
    void tagsAfterLockSwapsOutTheAutomaticTag() {
        assertEquals(Optional.of(List.of(BUG, REVIEWED)), ChannelCommands.tagsAfterLock(List.of(OPEN, BUG), REVIEWED, OPEN, false));
    }

    @Test
    void tagsAfterLockIsEmptyWhenAlreadyTaggedAndLocked() {
        assertEquals(Optional.empty(), ChannelCommands.tagsAfterLock(List.of(REVIEWED), REVIEWED, null, true));
    }

    @Test
    void lockingASuggestionTagsLocksAndAnnounces() {
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(BUG), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals(List.of(BUG, REVIEWED), new ArrayList<>((Collection<?>) recorder.last("setAppliedTags").arg(0)));
        assertEquals(true, recorder.last("setLocked").arg(0));
        assertTrue(((String) recorder.last("sendMessage").arg(0)).contains("applied the Reviewed tag and locked this suggestion!"));
        assertEquals("Successfully locked thread!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aTaggedThreadThatWasUnlockedIsLockedAgain() {
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(REVIEWED), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals(true, recorder.last("setLocked").arg(0));
        assertTrue(((String) recorder.last("sendMessage").arg(0)).endsWith("locked this suggestion!"));
        assertFalse(((String) recorder.last("sendMessage").arg(0)).contains("applied"));
    }

    @Test
    void aTaggedLockedThreadIsLeftAlone() {
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(REVIEWED), true);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals("This thread is already tagged and locked!", DiscordFakes.lastEditText(recorder));
        assertFalse(recorder.called("setLocked"));
    }

    @Test
    void theAutomaticTagIsRemovedWhenConfigured() {
        config.getChannelConfig().setForumAutoTags(List.of(new ForumAutoTag(SUGGESTIONS_FORUM, "Open", "Reviewed")));
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(OPEN), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals(List.of(REVIEWED), new ArrayList<>((Collection<?>) recorder.last("setAppliedTags").arg(0)));
    }

    @Test
    void outsideSuggestionsAModeratorsOwnTagIsUsed() {
        config.getChannelConfig().setCustomForumTags(List.of(new CustomForumTag(MODERATOR, "Callum's Pick")));
        Thread thread = thread(OTHER_FORUM, List.of(), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals(List.of(MINE), new ArrayList<>((Collection<?>) recorder.last("setAppliedTags").arg(0)));
    }

    @Test
    void aMissingTagIsReported() {
        config.getSuggestionConfig().setReviewedTag("Approved");
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals("I could not find a tag with the name `Approved`!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void onlyForumThreadsCanBeLocked() {
        MessageChannelUnion textChannel = Fakes.of(MessageChannelUnion.class, Map.of("getType", ChannelType.TEXT));

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), textChannel);

        assertEquals("This command is only available in threads!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aFailedUpdateIsReported() {
        managerSucceeds = false;
        Thread thread = thread(SUGGESTIONS_FORUM, List.of(), false);

        commands.lock(DiscordFakes.slash(recorder, MODERATOR), thread.channel());

        assertEquals("Failed to lock the thread. Please check my permissions and try again.", DiscordFakes.lastEditText(recorder));
        assertFalse(recorder.called("sendMessage"));
    }

    private record Thread(MessageChannelUnion channel) {
    }

    private Thread thread(String forumId, List<ForumTag> applied, boolean locked) {
        ForumChannel forum = Fakes.of(ForumChannel.class, Map.of(
            "getId", forumId,
            "getName", "forum",
            "getAvailableTags", List.of(REVIEWED, OPEN, BUG, MINE)));
        IThreadContainerUnion parent = Fakes.of(IThreadContainerUnion.class, Map.of("getType", ChannelType.FORUM, "asForumChannel", forum));
        ThreadChannelManager manager = recorder.fake(ThreadChannelManager.class, Map.of("queue", (CallRecorder.Answer) args -> {
            if (managerSucceeds && args.length > 0 && args[0] != null) {
                consumer(args[0]).accept(null);
            } else if (!managerSucceeds && args.length > 1 && args[1] != null) {
                consumer(args[1]).accept(new IllegalStateException("missing permission"));
            }
            return null;
        }));
        ThreadChannel thread = Fakes.of(ThreadChannel.class, Map.of(
            "getParentChannel", parent,
            "getAppliedTags", applied,
            "isLocked", locked,
            "getManager", manager));
        // sendMessage(String) is a default method that builds JDA's own action class, which needs a real JDA
        CallRecorder.Answer sendMessage = args -> recorder.fake(MessageCreateAction.class);
        return new Thread(recorder.fake(MessageChannelUnion.class, Map.of(
            "getType", ChannelType.GUILD_PUBLIC_THREAD,
            "asThreadChannel", thread,
            "sendMessage", sendMessage)));
    }

    private static ForumTag tag(String name) {
        return Fakes.of(ForumTag.class, Map.of("getName", name));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }
}
