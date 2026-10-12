package net.hypixel.nerdbot.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumChannel;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaProjectConfigUpdaterTest {

    private final NerdBotConfig config = new NerdBotConfig();

    @BeforeEach
    void setUp() {
        config.getSuggestionConfig().setForumChannelId("500");
        config.getChannelConfig().setProjectChannelNames(new String[]{"project"});
    }

    @Test
    void forumsAreSortedIntoAlphaAndProjectByName() {
        boolean changed = AlphaProjectConfigUpdater.updateForumIds(config, List.of(
            forumChannel("1", "alpha-suggestions"), forumChannel("2", "project-ideas"), forumChannel("3", "general")));

        assertTrue(changed);
        assertArrayEquals(new String[]{"1"}, config.getAlphaProjectConfig().getAlphaForumIds());
        assertArrayEquals(new String[]{"2"}, config.getAlphaProjectConfig().getProjectForumIds());
    }

    @Test
    void nothingChangesWhenTheForumsAreAlreadyListed() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"1"});
        config.getAlphaProjectConfig().setProjectForumIds(new String[]{"2"});

        assertFalse(AlphaProjectConfigUpdater.updateForumIds(config, List.of(forumChannel("1", "alpha-suggestions"), forumChannel("2", "project-ideas"))));
    }

    @Test
    void aForumListedByHandKeepsItsType() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"7"});

        boolean changed = AlphaProjectConfigUpdater.updateForumIds(config, List.of(forumChannel("7", "secret-testing")));

        assertFalse(changed);
        assertArrayEquals(new String[]{"7"}, config.getAlphaProjectConfig().getAlphaForumIds());
    }

    @Test
    void aForumThatNoLongerExistsIsDropped() {
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{"1", "9"});

        boolean changed = AlphaProjectConfigUpdater.updateForumIds(config, List.of(forumChannel("1", "alpha-suggestions")));

        assertTrue(changed);
        assertArrayEquals(new String[]{"1"}, config.getAlphaProjectConfig().getAlphaForumIds());
    }

    @Test
    void theMainSuggestionForumIsNeverAlphaOrProject() {
        AlphaProjectConfigUpdater.updateForumIds(config, List.of(forumChannel("500", "alpha-named-suggestions")));

        assertArrayEquals(new String[0], config.getAlphaProjectConfig().getAlphaForumIds());
    }
}
