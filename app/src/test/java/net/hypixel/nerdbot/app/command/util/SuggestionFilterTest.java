package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.app.command.SuggestionStats;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.AGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.DISAGREE;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.NOW;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.config;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.firstMessage;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.reaction;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.tag;
import static net.hypixel.nerdbot.app.testsupport.SuggestionFixtures.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SuggestionFilterTest {

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = config("10");
    private final Map<String, ThreadChannel> threads = new HashMap<>();
    private final JDA jda = Fakes.of(JDA.class, Map.of("getThreadChannelById", (CallRecorder.Answer) args -> threads.get(String.valueOf(args[0]))));
    private Set<String> hiddenThreads = Set.of();
    private final Member viewer = Fakes.of(Member.class, Map.of("hasPermission", (CallRecorder.Answer) args ->
        !hiddenThreads.contains(((ThreadChannel) args[0]).getId())));

    private final Suggestion bugFix = suggestion(1, "Fix the auction house", List.of(tag("Bug"), tag("Greenlit")), Suggestion.ChannelType.NORMAL, true);
    private final Suggestion newItem = suggestion(2, "Add a new sword", List.of(tag("Item")), Suggestion.ChannelType.NORMAL, true);
    private final Suggestion alpha = suggestion(3, "Alpha sword tweak", List.of(tag("Item")), Suggestion.ChannelType.ALPHA, true);
    private final Suggestion deleted = suggestion(4, "Gone", List.of(), Suggestion.ChannelType.NORMAL, false);
    private final List<Suggestion> all = List.of(bugFix, newItem, alpha, deleted);

    @Test
    void onlyTheRequestedTypeAndNoDeletedOnes() {
        assertEquals(List.of(bugFix, newItem), filter(null, null, null, Suggestion.ChannelType.NORMAL));
        assertEquals(List.of(alpha), filter(null, null, null, Suggestion.ChannelType.ALPHA));
    }

    @Test
    void includedTagsMustAllBePresentIgnoringCase() {
        assertEquals(List.of(bugFix), filter(null, "bug, greenlit", null, Suggestion.ChannelType.NORMAL));
        assertEquals(List.of(), filter(null, "bug, item", null, Suggestion.ChannelType.NORMAL));
    }

    @Test
    void excludedTagsMustAllBeAbsent() {
        assertEquals(List.of(newItem), filter(null, "!bug", null, Suggestion.ChannelType.NORMAL));
    }

    @Test
    void titleMatchesAnywhereIgnoringCase() {
        assertEquals(List.of(newItem), filter(null, null, "SWORD", Suggestion.ChannelType.NORMAL));
    }

    @Test
    void anOwnersSuggestionsOnlyIncludeOnesTheViewerCanSee() {
        assertEquals(List.of(bugFix, newItem), filter(500L, null, null, Suggestion.ChannelType.NORMAL));

        hiddenThreads = Set.of("1");
        assertEquals(List.of(newItem), filter(500L, null, null, Suggestion.ChannelType.NORMAL));
        assertEquals(List.of(), filter(999L, null, null, Suggestion.ChannelType.NORMAL));
    }

    @Test
    void statsOnlyCountWhatTheViewerCanSee() {
        hiddenThreads = Set.of("2");

        assertEquals(new SuggestionStats(1, 1, 5, 1), SuggestionCommandUtils.buildSuggestionStats(jda, List.of(bugFix, newItem), viewer));
    }

    @Test
    void filtersAreListedForTheEmbed() {
        SuggestionCommandUtils.Listing listing = new SuggestionCommandUtils.Listing(viewer, "bug", "sword", Suggestion.ChannelType.ALPHA,
            false, false, null, null, false, jda);

        assertEquals("- Filtered by tags: `bug`\n- Filtered by title: `sword`\n- Filtered by type: `Alpha`", SuggestionCommandUtils.filtersText(listing));
    }

    private List<Suggestion> filter(Long ownerId, String tags, String title, Suggestion.ChannelType type) {
        return SuggestionCommandUtils.filterSuggestions(jda, all, viewer, ownerId, tags, title, type);
    }

    private Suggestion suggestion(long id, String name, List<ForumTag> tags, Suggestion.ChannelType type, boolean hasStarter) {
        Map<String, Object> answers = new HashMap<>();
        ThreadChannel base = thread(recorder, id, "10", NOW, false, false, tags);
        answers.put("getId", base.getId());
        answers.put("getIdLong", base.getIdLong());
        answers.put("getName", name);
        answers.put("getOwnerId", base.getOwnerId());
        answers.put("getOwnerIdLong", base.getOwnerIdLong());
        answers.put("getGuild", base.getGuild());
        answers.put("getTimeCreated", base.getTimeCreated());
        answers.put("getParentChannel", base.getParentChannel());
        answers.put("getAppliedTags", tags);
        ThreadChannel named = Fakes.of(ThreadChannel.class, answers);
        threads.put(String.valueOf(id), named);
        return Suggestion.of(named, type, hasStarter ? firstMessage(id, 500, NOW, reaction(AGREE, 5), reaction(DISAGREE, 1)) : null, config);
    }
}
