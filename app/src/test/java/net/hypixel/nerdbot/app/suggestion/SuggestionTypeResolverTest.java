package net.hypixel.nerdbot.app.suggestion;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.thread;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SuggestionTypeResolverTest {

    private final NerdBotConfig config = new NerdBotConfig();

    @BeforeEach
    void setUp() {
        config.getSuggestionConfig().setForumChannelId("500");
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"501"});
        config.getAlphaProjectConfig().setProjectForumIds(new String[]{"502"});
    }

    @Test
    void configuredForumsHaveTheirConfiguredType() {
        assertEquals(Optional.of(Suggestion.ChannelType.NORMAL), SuggestionTypeResolver.getConfiguredForumType("500", config));
        assertEquals(Optional.of(Suggestion.ChannelType.ALPHA), SuggestionTypeResolver.getConfiguredForumType("501", config));
        assertEquals(Optional.of(Suggestion.ChannelType.PROJECT), SuggestionTypeResolver.getConfiguredForumType("502", config));
    }

    @Test
    void otherForumsHaveNoConfiguredType() {
        assertEquals(Optional.empty(), SuggestionTypeResolver.getConfiguredForumType("999", config));
    }

    @Test
    void withNoAlphaOrProjectForumsConfiguredOnlyTheSuggestionForumMatches() {
        config.getAlphaProjectConfig().setAlphaForumIds(null);
        config.getAlphaProjectConfig().setProjectForumIds(null);

        assertEquals(Optional.empty(), SuggestionTypeResolver.getConfiguredForumType("501", config));
        assertEquals(Optional.of(Suggestion.ChannelType.NORMAL), SuggestionTypeResolver.getConfiguredForumType("500", config));
    }

    @Test
    void aThreadInAForumTakesTheForumsType() {
        assertEquals(Suggestion.ChannelType.ALPHA, SuggestionTypeResolver.getThreadSuggestionType(thread("1", forumParent("501", "anything")), config));
    }

    @Test
    void aThreadInATextChannelIsJudgedByTheChannelsName() {
        assertEquals(Suggestion.ChannelType.ALPHA, SuggestionTypeResolver.getThreadSuggestionType(thread("1", textParent("10", "alpha-chat")), config));
        assertEquals(Suggestion.ChannelType.NORMAL, SuggestionTypeResolver.getThreadSuggestionType(thread("2", textParent("11", "general")), config));
    }
}
