package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textChannel;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.thread;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionListenerTest {

    private static final String REVIEWED = "Reviewed";

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeConfigStore store = new FakeConfigStore(config);
    private final CallRecorder recorder = new CallRecorder();
    private final List<ForumChannel> guildForums = new ArrayList<>();
    private final Guild guild = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID, "getForumChannels", (CallRecorder.Answer) args -> List.copyOf(guildForums)));
    // Never started, so it holds nothing and schedules nothing
    private final SuggestionCache suggestions = new SuggestionCache(null, null, () -> config, null, null);
    private final SuggestionListener listener = new SuggestionListener(new GuildScope(() -> config), suggestions, () -> config, store);

    @BeforeEach
    void setUp() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getSuggestionConfig().setForumChannelId("500");
        config.getSuggestionConfig().setReviewedTag(REVIEWED);
        config.getAlphaProjectConfig().setAutoCreateTags(true);
    }

    private ForumChannel forum(String id, String name, String... tagNames) {
        List<ForumTag> tags = new ArrayList<>();
        for (String tagName : tagNames) {
            tags.add(Fakes.of(ForumTag.class, Map.of("getName", tagName)));
        }
        Map<String, Object> answers = new HashMap<>(Map.of(
            "getId", id, "getName", name, "getType", ChannelType.FORUM, "getGuild", guild, "getAvailableTags", tags));
        ForumChannel forum = recorder.fake(ForumChannel.class, answers);
        guildForums.add(forum);
        return forum;
    }

    @Test
    void postsInConfiguredForumsAreSuggestions() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"501"});

        assertTrue(listener.suggestionThread(thread("1", forumParent("500", "suggestions"))).isPresent());
        assertTrue(listener.suggestionThread(thread("2", forumParent("501", "alpha"))).isPresent());
    }

    @Test
    void otherChannelsAreNotSuggestions() {
        assertEquals(Optional.empty(), listener.suggestionThread(thread("1", forumParent("999", "elsewhere"))));
        assertEquals(Optional.empty(), listener.suggestionThread(textChannel("500", "not-a-thread")));
    }

    @Test
    void aNewAlphaForumIsAddedTaggedAndSaved() {
        ForumChannel alpha = forum("501", "alpha-suggestions");

        listener.forumChanged(alpha);

        assertArrayEquals(new String[]{"501"}, config.getAlphaProjectConfig().getAlphaForumIds());
        ForumAutoTag autoTag = config.getChannelConfig().getForumAutoTagConfig("501");
        assertEquals(SuggestionListener.SUBMITTED_TAG, autoTag.getDefaultTagName());
        assertEquals(REVIEWED, autoTag.getReviewTagName());
        assertTrue(recorder.called("setAvailableTags"));
        assertEquals(1, store.saves());
    }

    @Test
    void aCustomisedAutoTagIsKept() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"501"});
        config.getChannelConfig().addForumAutoTagConfigIfAbsent("501", "Open", "Done");
        ForumChannel alpha = forum("501", "alpha-suggestions", SuggestionListener.SUBMITTED_TAG, REVIEWED);

        listener.forumChanged(alpha);

        ForumAutoTag autoTag = config.getChannelConfig().getForumAutoTagConfig("501");
        assertEquals("Open", autoTag.getDefaultTagName());
        assertEquals("Done", autoTag.getReviewTagName());
        assertEquals(0, store.saves());
    }

    @Test
    void tagsTheForumAlreadyHasAreNotRecreated() {
        ForumChannel alpha = forum("501", "alpha-suggestions", "submitted", REVIEWED);

        listener.forumChanged(alpha);

        assertFalse(recorder.called("setAvailableTags"));
    }

    @Test
    void withAutoCreateOffNoTagsAreCreated() {
        config.getAlphaProjectConfig().setAutoCreateTags(false);

        listener.forumChanged(forum("501", "alpha-suggestions"));

        assertFalse(recorder.called("setAvailableTags"));
        assertEquals(1, store.saves());
    }

    @Test
    void anOrdinaryForumChangesNothing() {
        listener.forumChanged(forum("700", "general-forum"));

        assertNull(config.getChannelConfig().getForumAutoTagConfig("700"));
        assertEquals(0, store.saves());
    }

    @Test
    void aDeletedForumIsForgotten() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"501"});
        config.getChannelConfig().addForumAutoTagConfigIfAbsent("501", SuggestionListener.SUBMITTED_TAG, REVIEWED);
        ForumChannel alpha = forum("501", "alpha-suggestions");
        guildForums.remove(alpha);

        listener.forumDeleted(alpha);

        assertArrayEquals(new String[0], config.getAlphaProjectConfig().getAlphaForumIds());
        assertNull(config.getChannelConfig().getForumAutoTagConfig("501"));
        assertEquals(1, store.saves());
    }

    @Test
    void aFailedSaveKeepsTheChangeForNow() {
        store.failWrites();

        assertDoesNotThrow(() -> listener.forumChanged(forum("501", "alpha-suggestions")));

        assertArrayEquals(new String[]{"501"}, config.getAlphaProjectConfig().getAlphaForumIds());
    }
}
