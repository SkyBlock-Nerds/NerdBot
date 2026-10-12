package net.hypixel.nerdbot.app.curator;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.testsupport.FakeGreenlitStore;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForumChannelCuratorTest {

    private static final String AGREE = "1";
    private static final String DISAGREE = "2";
    private static final String NEUTRAL = "3";
    private static final String GREENLIT_EMOJI = "4";

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeGreenlitStore greenlit = new FakeGreenlitStore();
    private final ManualScheduledExecutor scheduler = new ManualScheduledExecutor();
    private final SuggestionCache suggestions = new SuggestionCache(scheduler, null, () -> config, null, null);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));
    private final CallRecorder recorder = new CallRecorder();
    private final ForumTag greenlitTag = tag("Greenlit");
    private final List<ForumTag> forumTags = new ArrayList<>(List.of(greenlitTag));
    private final List<ThreadChannel> threads = new ArrayList<>();
    private final Map<ThreadChannel, Message> firstMessages = new HashMap<>();
    private final ForumChannel forum = Fakes.of(ForumChannel.class, Map.of(
        "getId", "500", "getName", "suggestions",
        "getAvailableTags", (CallRecorder.Answer) args -> List.copyOf(forumTags),
        "getThreadChannels", (CallRecorder.Answer) args -> List.copyOf(threads)));
    private boolean readOnly;
    private ThreadChannel failing;
    private CountDownLatch holdCuration;
    private CountDownLatch curationStarted;

    private final ForumChannelCurator curator = new ForumChannelCurator(() -> config, greenlit, suggestions,
        () -> Fakes.of(BotState.class, Map.of("isReadOnly", readOnly)), clock) {
        @Override
        Optional<Message> firstMessageOf(ThreadChannel thread) {
            if (curationStarted != null) {
                curationStarted.countDown();
                await(holdCuration);
            }
            if (thread == failing) {
                throw new IllegalStateException("Unknown channel");
            }
            return Optional.ofNullable(firstMessages.get(thread));
        }

        @Override
        GreenlitMessage describe(Message message, ThreadChannel thread, int agree, int neutral, int disagree, EmojiConfig emojiConfig) {
            return GreenlitMessage.builder().messageId(message.getId()).agrees(agree).neutrals(neutral).disagrees(disagree).suggestionContent("idea").build();
        }
    };

    @BeforeEach
    void setUp() {
        config.getSuggestionConfig().setGreenlitTag("Greenlit");
        config.getSuggestionConfig().setGreenlitThreshold(3);
        config.getSuggestionConfig().setGreenlitRatio(50);
        EmojiConfig emojis = config.getEmojiConfig();
        emojis.setAgreeEmojiId(AGREE);
        emojis.setDisagreeEmojiId(DISAGREE);
        emojis.setNeutralEmojiId(NEUTRAL);
        emojis.setGreenlitEmojiId(GREENLIT_EMOJI);
        for (String id : List.of(AGREE, DISAGREE, NEUTRAL, GREENLIT_EMOJI)) {
            EmojiCache.getEmojiCache().put(id, Emoji.fromCustom("emoji" + id, Long.parseLong(id), false));
        }
    }

    @AfterEach
    void clearEmojis() {
        EmojiCache.getEmojiCache().clear();
    }

    private static ForumTag tag(String name) {
        return Fakes.of(ForumTag.class, Map.of("getName", name, "getId", name, "getIdLong", (long) name.hashCode()));
    }

    private static MessageReaction reaction(String emojiId, int count) {
        return new MessageReaction(Fakes.of(JDA.class, Map.of()), Fakes.of(MessageChannel.class, Map.of()),
            (EmojiUnion) Emoji.fromCustom("emoji" + emojiId, Long.parseLong(emojiId), false), 1, 2, new boolean[]{false, false}, new int[]{count, 0});
    }

    /** An open post whose first message has {@code agree} agrees and {@code disagree} disagrees. */
    private ThreadChannel post(String id, int agree, int disagree, ForumTag... applied) {
        ThreadChannel thread = recorder.fake(ThreadChannel.class, Map.of(
            "getId", id, "getName", "post-" + id, "getOwnerId", "9",
            "getAppliedTags", List.of(applied)));
        Message message = Fakes.of(Message.class, Map.of(
            "getId", id, "getReactions", List.of(reaction(AGREE, agree), reaction(DISAGREE, disagree))));
        threads.add(thread);
        firstMessages.put(thread, message);
        return thread;
    }

    private Curation curate(boolean dryRun) {
        Curation curation = new Curation(dryRun);
        assertTrue(curator.curate(forum, curation));
        return curation;
    }

    private List<String> savedIds() {
        return greenlit.saved.stream().map(GreenlitMessage::getMessageId).toList();
    }

    @Test
    void aPostWithEnoughVotesIsGreenlitAndSaved() {
        post("10", 4, 1);

        Curation curation = curate(false);

        assertEquals(1, curation.greenlit().size());
        assertEquals(List.of("10"), savedIds());
        List<ForumTag> appliedTags = (List<ForumTag>) recorder.last("setAppliedTags").arg(0);
        assertEquals(List.of(greenlitTag), appliedTags);
        assertTrue(recorder.called("complete"));
        assertEquals(1, scheduler.pendingDelays().size(), "the suggestion should be refreshed");
    }

    @Test
    void aPostWithoutEnoughVotesIsLeftAlone() {
        post("10", 2, 0);
        post("11", 3, 4);

        Curation curation = curate(false);

        assertTrue(curation.greenlit().isEmpty());
        assertTrue(greenlit.saved.isEmpty());
        assertFalse(recorder.called("setAppliedTags"));
    }

    @Test
    void anAlreadyGreenlitPostOnlyHasItsVotesSaved() {
        post("10", 9, 0, greenlitTag);

        Curation curation = curate(false);

        assertTrue(curation.greenlit().isEmpty());
        assertEquals(List.of("10"), savedIds());
        assertFalse(recorder.called("setAppliedTags"));
    }

    @Test
    void aDryRunChangesAndSavesNothing() {
        post("10", 4, 0);
        post("11", 9, 0, greenlitTag);

        Curation curation = curate(true);

        assertTrue(curation.greenlit().isEmpty());
        assertTrue(greenlit.saved.isEmpty());
        assertFalse(recorder.called("setAppliedTags"));
    }

    @Test
    void inReadOnlyModeEveryCurationIsADryRun() {
        readOnly = true;
        post("10", 4, 0);

        curate(false);

        assertTrue(greenlit.saved.isEmpty());
        assertFalse(recorder.called("setAppliedTags"));
    }

    @Test
    void onePostFailingDoesNotStopTheRest() {
        failing = post("10", 4, 0);
        post("11", 4, 0);

        Curation curation = curate(false);

        assertEquals(List.of("11"), savedIds());
        assertEquals(1, curation.greenlit().size());
    }

    @Test
    void aPostWhoseTagsCantChangeIsNotRecorded() {
        ThreadChannel thread = post("10", 4, 0);
        CallRecorder rejecting = new CallRecorder();
        threads.remove(thread);
        ThreadChannel locked = rejecting.fake(ThreadChannel.class, Map.of("getId", "10", "getName", "post-10", "getAppliedTags", List.of(),
            "getManager", rejecting.fake(ThreadChannelManager.class, Map.of(
                "complete", (CallRecorder.Answer) args -> {
                    throw new IllegalStateException("Missing permissions");
                }))));
        threads.add(locked);
        firstMessages.put(locked, firstMessages.get(thread));

        Curation curation = curate(false);

        assertTrue(curation.greenlit().isEmpty());
        assertTrue(greenlit.saved.isEmpty());
    }

    @Test
    void aPostWithFiveTagsIsNotGreenlitAndItsOwnerIsTold() {
        post("10", 4, 0, tag("a"), tag("b"), tag("c"), tag("d"), tag("e"));

        Curation curation = curate(false);

        assertTrue(curation.greenlit().isEmpty());
        assertTrue(recorder.called("sendMessageEmbeds"));
        assertFalse(recorder.called("setAppliedTags"));
    }

    @Test
    void theDefaultTagComesOffWhenTheGreenlitTagIsTheReviewTag() {
        ForumTag submitted = tag("Submitted");
        forumTags.add(submitted);
        config.getChannelConfig().addForumAutoTagConfigIfAbsent("500", "Submitted", "Greenlit");
        post("10", 4, 0, submitted);

        curate(false);

        assertEquals(List.of(greenlitTag), recorder.last("setAppliedTags").arg(0));
    }

    @Test
    void aForumWithoutTheGreenlitTagIsNotCurated() {
        forumTags.clear();
        post("10", 4, 0);

        Curation curation = curate(false);

        assertTrue(curation.greenlit().isEmpty());
        assertTrue(greenlit.saved.isEmpty());
    }

    @Test
    void missingVotingEmojisStopTheCuration() {
        EmojiCache.getEmojiCache().remove(GREENLIT_EMOJI);
        post("10", 4, 0);

        curate(false);

        assertTrue(greenlit.saved.isEmpty());
    }

    @Test
    void onlyOneCurationRunsAtATime() throws Exception {
        post("10", 4, 0);
        curationStarted = new CountDownLatch(1);
        holdCuration = new CountDownLatch(1);

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> curator.curate(forum, new Curation(false)));
        assertTrue(curationStarted.await(5, TimeUnit.SECONDS));

        assertFalse(curator.curate(forum, new Curation(false)));

        holdCuration.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
