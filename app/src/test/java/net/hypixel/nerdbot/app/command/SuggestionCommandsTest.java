package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.ReviewRequestConfig;
import net.hypixel.nerdbot.app.config.SuggestionConfig;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeGreenlitStore;
import net.hypixel.nerdbot.app.testsupport.FakeSuggestionFactory;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.AGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.DISAGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.NOW;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.config;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.firstMessage;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.reaction;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.tag;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionCommandsTest {

    private static final String FORUM = "10";
    private static final String REVIEW_CHANNEL = "70";
    private static final long AUTHOR = 500;
    private static final long THREAD = 42;

    private final CallRecorder recorder = new CallRecorder();
    private final ManualScheduledExecutor scheduler = new ManualScheduledExecutor();
    private final NerdBotConfig config = config(FORUM);
    private final SuggestionConfig suggestionConfig = config.getSuggestionConfig();
    private final FakeSuggestionFactory factory = new FakeSuggestionFactory(config);
    private final FakeGreenlitStore greenlit = new FakeGreenlitStore();
    private final Map<String, Object> threads = new HashMap<>();
    private TextChannel reviewChannel;
    private boolean reviewSendSucceeds = true;
    private final JDA jda = Fakes.of(JDA.class, Map.of(
        "getTextChannelById", (CallRecorder.Answer) args -> REVIEW_CHANNEL.equals(args[0]) ? reviewChannel : null,
        "getThreadChannelById", (CallRecorder.Answer) args -> threads.get(String.valueOf(args[0]))));
    private final SuggestionCache cache = new SuggestionCache(scheduler, factory, () -> config, jda, Clock.systemUTC());
    private final SuggestionCommands commands = new SuggestionCommands(cache, factory, () -> config, jda, greenlit, Runnable::run,
        Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    private ThreadChannel thread;

    @BeforeEach
    void setUp() {
        suggestionConfig.setReviewRequestConfig(new ReviewRequestConfig());
        suggestionConfig.getReviewRequestConfig().setEnabled(true);
        suggestionConfig.getReviewRequestConfig().setChannelId(REVIEW_CHANNEL);
        suggestionConfig.getReviewRequestConfig().setThreshold(2);
        suggestionConfig.getReviewRequestConfig().setMinimumSuggestionAge(Duration.ofDays(1).toMillis());
        suggestionConfig.getReviewRequestConfig().setEnforceGreenlitRatio(false);

        reviewChannel = recorder.fake(TextChannel.class, Map.of("sendMessageEmbeds", (CallRecorder.Answer) args ->
            recorder.fake(MessageCreateAction.class, Map.of("queue", (CallRecorder.Answer) queueArgs -> {
                if (reviewSendSucceeds && queueArgs.length > 0 && queueArgs[0] != null) {
                    consumer(queueArgs[0]).accept(null);
                } else if (!reviewSendSucceeds && queueArgs.length > 1 && queueArgs[1] != null) {
                    consumer(queueArgs[1]).accept(new IllegalStateException("missing permission"));
                }
                return null;
            }))));

        thread = suggestionThread(List.of(), true);
        factory.firstMessage(String.valueOf(THREAD), firstMessage(THREAD, AUTHOR, NOW.minusDays(3), reaction(AGREE, 5), reaction(DISAGREE, 1)));
        cache.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Nested
    class ReviewRules {

        private Suggestion suggestion() {
            return cache.find(String.valueOf(THREAD)).orElseThrow();
        }

        private Optional<String> problem(Optional<Message> first, long requester) {
            return SuggestionCommands.reviewProblem(suggestion(), first, requester, suggestionConfig.getGreenlitRatio(),
                suggestionConfig.getReviewRequestConfig(), NOW.toInstant().toEpochMilli());
        }

        @Test
        void aGoodRequestHasNoProblem() {
            assertEquals(Optional.empty(), problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), AUTHOR));
        }

        @Test
        void eachRuleHasItsMessage() {
            assertEquals("The original message for this suggestion was deleted!", problem(Optional.empty(), AUTHOR).orElseThrow());
            assertEquals("You cannot request a review on a thread you did not create!",
                problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), 999).orElseThrow());
            assertEquals("This suggestion is too new to request a review!",
                problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusHours(2))), AUTHOR).orElseThrow());
        }

        @Test
        void tooFewAgrees() {
            suggestionConfig.getReviewRequestConfig().setThreshold(6);

            assertEquals("You need at least 6 agrees to request a review!",
                problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), AUTHOR).orElseThrow());
        }

        @Test
        void theRatioOnlyCountsWhenEnforced() {
            suggestionConfig.setGreenlitRatio(90);
            assertEquals(Optional.empty(), problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), AUTHOR));

            suggestionConfig.getReviewRequestConfig().setEnforceGreenlitRatio(true);
            assertEquals("You need at least a 90% agree ratio to request a review!",
                problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), AUTHOR).orElseThrow());

            suggestionConfig.setGreenlitRatio(87.5);
            assertEquals("You need at least a 87.5% agree ratio to request a review!",
                problem(Optional.of(firstMessage(THREAD, AUTHOR, NOW.minusDays(3))), AUTHOR).orElseThrow());
        }
    }

    @Nested
    class RequestReview {

        @Test
        void aGoodRequestIsSentWithButtonsAndStartsTheCooldown() {
            commands.requestReview(slash(AUTHOR), inThread());

            MessageEmbed embed = (MessageEmbed) recorder.last("sendMessageEmbeds").arg(0);
            assertEquals("Greenlit Review Request from Author", embed.getAuthor().getName());
            assertTrue(recorder.called("deleteOriginal"));
            assertEquals(SuggestionCommands.SENT_FOR_REVIEW, recorder.last("sendMessage").arg(0));

            commands.requestReview(slash(AUTHOR), inThread());
            assertEquals("You cannot request another review on this thread yet!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void aMissingReviewChannelTellsTheMemberAndStartsNoCooldown() {
            suggestionConfig.getReviewRequestConfig().setChannelId("71");

            commands.requestReview(slash(AUTHOR), inThread());
            assertEquals("Review requests aren't set up right now. Please let a moderator know.", DiscordFakes.lastEditText(recorder));
            assertFalse(recorder.called("sendMessage"), "the member isn't told it was sent");

            suggestionConfig.getReviewRequestConfig().setChannelId(REVIEW_CHANNEL);
            commands.requestReview(slash(AUTHOR), inThread());
            assertTrue(recorder.called("sendMessageEmbeds"));
        }

        @Test
        void aFailedSendStartsNoCooldown() {
            reviewSendSucceeds = false;

            commands.requestReview(slash(AUTHOR), inThread());
            assertEquals("Couldn't send the review request. Please try again later.", DiscordFakes.lastEditText(recorder));

            reviewSendSucceeds = true;
            commands.requestReview(slash(AUTHOR), inThread());
            assertTrue(recorder.called("deleteOriginal"));
        }

        @Test
        void aConfigWithoutTheReviewSectionMeansOff() {
            suggestionConfig.setReviewRequestConfig(null);

            commands.requestReview(slash(AUTHOR), inThread());

            assertEquals("Review requests are currently disabled!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void disabledReviewsSaySo() {
            suggestionConfig.getReviewRequestConfig().setEnabled(false);

            commands.requestReview(slash(AUTHOR), inThread());

            assertEquals("Review requests are currently disabled!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void onlyInSuggestionThreads() {
            commands.requestReview(slash(AUTHOR), Fakes.of(MessageChannelUnion.class, Map.of("getType", ChannelType.TEXT)));
            assertEquals("This command cannot be used here!", DiscordFakes.lastEditText(recorder));

            ThreadChannel elsewhere = thread(recorder, 43, "99");
            commands.requestReview(slash(AUTHOR), Fakes.of(MessageChannelUnion.class, Map.of(
                "getType", ChannelType.GUILD_PUBLIC_THREAD, "asThreadChannel", elsewhere)));
            assertEquals("You cannot send threads in non-suggestion channels for review!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void aSuggestionNotYetCachedSaysSo() {
            cache.remove(String.valueOf(THREAD));

            commands.requestReview(slash(AUTHOR), inThread());

            assertEquals("Couldn't find that suggestion in the cache! Please try again later!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void reviewButtonsUseTheExistingIdFormat() {
            List<Button> buttons = SuggestionCommands.reviewButtons("42", config.getEmojiConfig());

            assertEquals(List.of("suggestion-review-accept-42", "suggestion-review-deny-42", "suggestion-review-lock-42"),
                buttons.stream().map(Button::getId).toList());
        }

        @Test
        void aMalformedEmojiIdMeansNoEmoji() {
            assertNull(SuggestionCommands.customEmoji("greenlit", "not-an-id"));
            assertNull(SuggestionCommands.customEmoji("greenlit", " "));
            assertEquals(123L, ((net.dv8tion.jda.api.entities.emoji.CustomEmoji) SuggestionCommands.customEmoji("greenlit", "123")).getIdLong());
        }
    }

    @Nested
    class ReviewButtons {

        // Fetching the voters behind a reaction needs a real JDA, so the message here has none
        @BeforeEach
        void firstMessageWithoutReactions() {
            factory.firstMessage(String.valueOf(THREAD), firstMessage(THREAD, AUTHOR, NOW.minusDays(3)));
        }

        @Test
        void denyingTellsTheThreadAndMarksTheRequest() {
            commands.review(button("deny"));

            assertEquals(SuggestionCommands.DENIED_MESSAGE, recorder.last("sendMessage").arg(0));
            assertTrue(recorder.called("editOriginalComponents"));
        }

        @Test
        void lockingLocksAndTellsTheThread() {
            commands.review(button("lock"));

            assertEquals(true, recorder.last("setLocked").arg(0));
            assertEquals(SuggestionCommands.LOCKED_MESSAGE, recorder.last("sendMessage").arg(0));
            assertTrue(recorder.called("editOriginalComponents"));
        }

        @Test
        void aFailedLockDoesNotShowTheExceptionAndLeavesTheButtons() {
            thread = suggestionThread(List.of(), false);

            commands.review(button("lock"));

            assertEquals("Unable to lock the thread. Please check my permissions and try again.", lastFollowUp());
            assertFalse(recorder.called("editOriginalComponents"));
        }

        @Test
        void acceptingGreenlightsSavesAndRefreshes() {
            commands.review(button("accept"));

            assertEquals(1, greenlit.saved.size());
            assertEquals(String.valueOf(AUTHOR), greenlit.saved.getFirst().getUserId());
            assertEquals(SuggestionCommands.GREENLIT_MESSAGE, recorder.last("sendMessage").arg(0));
            assertTrue(recorder.called("setAppliedTags"));
            assertTrue(recorder.called("editOriginalComponents"));
            assertEquals(List.of(SuggestionCache.UPDATE_DELAY), scheduler.pendingDelays(), "the cached copy is refreshed");
        }

        @Test
        void aFailedSaveIsReportedWithoutTouchingTheReviewMessage() {
            greenlit.savesFail();

            commands.review(button("accept"));

            assertEquals("Couldn't greenlight the suggestion. Please try again.", lastFollowUp());
            assertFalse(recorder.called("editOriginal"));
            assertFalse(recorder.called("editOriginalComponents"));
        }

        @Test
        void anAlreadyGreenlitSuggestionIsNotGreenlitAgain() {
            thread = suggestionThread(List.of(tag("Greenlit")), true);

            commands.review(button("accept"));

            assertEquals("This suggestion has already been greenlit or reviewed!", lastFollowUp());
            assertTrue(greenlit.saved.isEmpty());
        }

        @Test
        void aForumWithoutTheGreenlitTagIsReportedAccurately() {
            suggestionConfig.setGreenlitTag("Approved");

            commands.review(button("accept"));

            assertEquals("The suggestion forum has no tag named `Approved`!", lastFollowUp());
        }

        @Test
        void anUnknownThreadIsReported() {
            threads.clear();

            commands.review(button("deny"));

            assertEquals("Could not find thread with ID 42!", lastFollowUp());
        }

        @Test
        void aMalformedButtonIdIsOutdated() {
            commands.review(recorder.fake(ButtonInteraction.class, Map.of("getComponentId", "suggestion-review-completed")));

            assertEquals(PanelId.OUTDATED, recorder.last("reply").arg(0));
        }

        private ButtonInteraction button(String action) {
            return recorder.fake(ButtonInteraction.class, Map.of(
                "getComponentId", "suggestion-review-" + action + "-" + THREAD,
                "getUser", DiscordFakes.user("1")));
        }

        private String lastFollowUp() {
            return (String) recorder.last("sendMessage").arg(0);
        }
    }

    @Nested
    class Listing {

        @Test
        void anUnknownTypeIsRefused() {
            commands.showSuggestions(slashAsMember(), new SuggestionCommands.ListingRequest(null, null, null, true, true), null, null, "nonsense");

            assertEquals("Invalid channel type provided!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void nothingMatchingSaysSo() {
            cache.start();
            scheduler.advance(Duration.ZERO);

            commands.showSuggestions(slashAsMember(), new SuggestionCommands.ListingRequest(null, null, null, true, true), null, "zebra", null);

            assertEquals("No suggestions found matching that filter!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void beforeTheFirstLoadItSaysResultsAreStillLoading() {
            SuggestionCache empty = new SuggestionCache(scheduler, factory, () -> config, jda, Clock.systemUTC());
            SuggestionCommands loading = new SuggestionCommands(empty, factory, () -> config, jda, greenlit, Runnable::run, Clock.systemUTC());

            loading.showSuggestions(slashAsMember(), new SuggestionCommands.ListingRequest(null, null, null, true, true), null, null, null);

            assertEquals("Suggestions are still loading, please try again in a minute.", DiscordFakes.lastEditText(recorder));
        }

        private SlashCommandInteraction slashAsMember() {
            Member member = Fakes.of(Member.class, Map.of("getId", "1", "getIdLong", 1L, "hasPermission", true));
            return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", DiscordFakes.user("1"), "getMember", member));
        }
    }

    private ThreadChannel suggestionThread(List<ForumTag> tags, boolean managerSucceeds) {
        ThreadChannel created = thread(recorder, THREAD, FORUM, NOW.minusDays(3), false, false, tags, managerSucceeds);
        threads.put(String.valueOf(THREAD), created);
        return created;
    }

    private MessageChannelUnion inThread() {
        return Fakes.of(MessageChannelUnion.class, Map.of("getType", ChannelType.GUILD_PUBLIC_THREAD, "asThreadChannel", thread));
    }

    private SlashCommandInteraction slash(long userId) {
        User user = Fakes.of(User.class, Map.of("getId", String.valueOf(userId), "getIdLong", userId, "getEffectiveName", "Author"));
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", user));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }
}
