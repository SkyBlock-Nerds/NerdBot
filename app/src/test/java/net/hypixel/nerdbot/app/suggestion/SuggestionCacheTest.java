package net.hypixel.nerdbot.app.suggestion;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.pagination.ThreadChannelPaginationAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.FakeSuggestionFactory;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.AGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.NOW;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.config;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.firstMessage;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.reaction;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionCacheTest {

    private static final String SUGGESTIONS = "10";
    private static final String ALPHA = "11";

    private final CallRecorder recorder = new CallRecorder();
    private final ManualScheduledExecutor scheduler = new ManualScheduledExecutor();
    private final NerdBotConfig config = config(SUGGESTIONS);
    private final FakeSuggestionFactory factory = new FakeSuggestionFactory(config);
    private final Map<String, ForumChannel> forums = new HashMap<>();
    private final JDA jda = Fakes.of(JDA.class, Map.of("getForumChannelById", (CallRecorder.Answer) args -> forums.get((String) args[0])));
    private final SuggestionCache cache = new SuggestionCache(scheduler, factory, () -> config, jda, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));

    @Test
    void votesWithinTheDelayAreOneFetch() {
        ThreadChannel thread = thread(recorder, 42, SUGGESTIONS);

        cache.requestUpdate(thread);
        cache.requestUpdate(thread);
        cache.requestUpdate(thread);
        assertTrue(factory.built.isEmpty(), "nothing is fetched on the caller's thread");

        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        assertEquals(List.of("42"), factory.built);
        assertTrue(cache.find("42").isPresent());
    }

    @Test
    void aVoteAfterTheUpdateFetchesAgain() {
        ThreadChannel thread = thread(recorder, 42, SUGGESTIONS);
        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        assertEquals(List.of("42", "42"), factory.built);
    }

    @Test
    void anUpdateUsesTheLatestVotes() {
        ThreadChannel thread = thread(recorder, 42, SUGGESTIONS);
        factory.firstMessage("42", firstMessage(42, 500, NOW, reaction(AGREE, 3)));

        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        assertEquals(3, cache.find("42").orElseThrow().getAgrees());
    }

    @Test
    void aFailedUpdateKeepsTheLastCopy() {
        ThreadChannel thread = thread(recorder, 42, SUGGESTIONS);
        factory.firstMessage("42", firstMessage(42, 500, NOW, reaction(AGREE, 3)));
        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        factory.failFor("42");
        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        assertEquals(3, cache.find("42").orElseThrow().getAgrees());
    }

    @Test
    void removingCancelsAPendingUpdate() {
        ThreadChannel thread = thread(recorder, 42, SUGGESTIONS);
        cache.requestUpdate(thread);

        cache.remove("42");
        scheduler.advance(SuggestionCache.UPDATE_DELAY);

        assertTrue(factory.built.isEmpty());
        assertTrue(cache.find("42").isEmpty());
    }

    @Test
    void refreshLoadsActiveAndArchivedThreadsOnce() {
        ThreadChannel active = thread(recorder, 1, SUGGESTIONS);
        ThreadChannel archived = thread(recorder, 2, SUGGESTIONS);
        forum(SUGGESTIONS, List.of(active), CompletableFuture.completedFuture(List.of(archived, active)));

        cache.refresh().join();

        assertEquals(List.of("1", "2"), factory.built);
        assertEquals(2, cache.all().size());
        assertTrue(cache.isInitialized());
    }

    @Test
    void threadsThatAreGoneAreDroppedButOnlyFromTheirOwnForum() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{ALPHA});
        forum(SUGGESTIONS, List.of(thread(recorder, 1, SUGGESTIONS), thread(recorder, 2, SUGGESTIONS)), CompletableFuture.completedFuture(List.of()));
        forum(ALPHA, List.of(thread(recorder, 3, ALPHA)), CompletableFuture.completedFuture(List.of()));
        cache.refresh().join();

        forum(SUGGESTIONS, List.of(thread(recorder, 1, SUGGESTIONS)), CompletableFuture.completedFuture(List.of()));
        cache.refresh().join();

        assertTrue(cache.find("1").isPresent());
        assertTrue(cache.find("2").isEmpty());
        assertTrue(cache.find("3").isPresent());
    }

    @Test
    void aForumThatFailsToLoadKeepsWhatWasCached() {
        forum(SUGGESTIONS, List.of(thread(recorder, 1, SUGGESTIONS)), CompletableFuture.completedFuture(List.of()));
        cache.refresh().join();

        forum(SUGGESTIONS, List.of(), CompletableFuture.failedFuture(new IllegalStateException("Discord is down")));
        cache.refresh().join();

        assertTrue(cache.find("1").isPresent());
    }

    @Test
    void aThreadThatFailsToBuildKeepsItsLastCopy() {
        ThreadChannel thread = thread(recorder, 1, SUGGESTIONS);
        forum(SUGGESTIONS, List.of(thread), CompletableFuture.completedFuture(List.of()));
        cache.refresh().join();

        factory.failFor("1");
        cache.refresh().join();

        assertTrue(cache.find("1").isPresent());
    }

    @Test
    void refreshArchivesQuietThreads() {
        config.getSuggestionConfig().setAutoArchiveThreshold(24);
        ThreadChannel quiet = thread(recorder, 1, SUGGESTIONS, NOW.minusDays(3), false, false, List.of());
        forum(SUGGESTIONS, List.of(quiet), CompletableFuture.completedFuture(List.of()));

        cache.refresh().join();

        assertEquals(true, recorder.last("setArchived").arg(0));
    }

    @Test
    void anUnsetOrMalformedForumIdIsSkipped() {
        config.getSuggestionConfig().setForumChannelId("");
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"not-an-id"});
        JDA strictJda = Fakes.of(JDA.class, Map.of("getForumChannelById", (CallRecorder.Answer) args -> {
            throw new NumberFormatException("not a snowflake");
        }));
        SuggestionCache strict = new SuggestionCache(scheduler, factory, () -> config, strictJda, Clock.systemUTC());

        strict.refresh().join();

        assertTrue(strict.isInitialized());
        assertTrue(strict.all().isEmpty());
    }

    @Test
    void startRefreshesNowAndThenEveryHourWithoutOverlap() {
        forum(SUGGESTIONS, List.of(thread(recorder, 1, SUGGESTIONS)), CompletableFuture.completedFuture(List.of()));

        cache.start();
        scheduler.advance(Duration.ZERO);
        assertEquals(1, factory.built.size());
        assertEquals(List.of(SuggestionCache.REFRESH_INTERVAL), scheduler.pendingDelays());

        scheduler.advance(SuggestionCache.REFRESH_INTERVAL);
        assertEquals(2, factory.built.size());
        assertEquals(1, scheduler.pendingDelays().size());
    }

    @Test
    void allIsNewestFirst() {
        forum(SUGGESTIONS, List.of(
            thread(recorder, 1, SUGGESTIONS, NOW.minusDays(2), false, false, List.of()),
            thread(recorder, 2, SUGGESTIONS, NOW, false, false, List.of())), CompletableFuture.completedFuture(List.of()));

        cache.refresh().join();

        assertEquals(List.of("2", "1"), cache.all().stream().map(Suggestion::getThreadId).toList());
    }

    @Test
    void shuttingDownStopsNewWorkQuietly() {
        scheduler.shutdownNow();

        cache.requestUpdate(thread(recorder, 1, SUGGESTIONS));
        cache.start();

        assertFalse(cache.isInitialized());
    }

    private void forum(String id, List<ThreadChannel> active, CompletableFuture<List<ThreadChannel>> archived) {
        ThreadChannelPaginationAction archivedAction = recorder.fake(ThreadChannelPaginationAction.class, Map.of("takeAsync", archived));
        forums.put(id, Fakes.of(ForumChannel.class, Map.of(
            "getId", id,
            "getName", "forum " + id,
            "getThreadChannels", active,
            "retrieveArchivedPublicThreadChannels", archivedAction)));
    }
}
