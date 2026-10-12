package net.hypixel.nerdbot.discord.config.channel;

import net.hypixel.nerdbot.discord.config.objects.ForumAutoTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelConfigAutoTagTest {

    private final ChannelConfig config = new ChannelConfig();

    @Test
    void aForumWithoutAutoTaggingGetsIt() {
        assertTrue(config.addForumAutoTagConfigIfAbsent("1", "Submitted", "Reviewed"));

        ForumAutoTag autoTag = config.getForumAutoTagConfig("1");
        assertEquals("Submitted", autoTag.getDefaultTagName());
        assertEquals("Reviewed", autoTag.getReviewTagName());
    }

    @Test
    void existingAutoTaggingIsLeftAsItWas() {
        config.addForumAutoTagConfigIfAbsent("1", "Open", "Done");

        assertFalse(config.addForumAutoTagConfigIfAbsent("1", "Submitted", "Reviewed"));

        assertEquals("Open", config.getForumAutoTagConfig("1").getDefaultTagName());
        assertEquals(1, config.getForumAutoTags().size());
    }

    @Test
    void removingTakesOnlyThatForumsAutoTagging() {
        config.addForumAutoTagConfigIfAbsent("1", "Submitted", "Reviewed");
        config.addForumAutoTagConfigIfAbsent("2", "Submitted", "Reviewed");

        assertTrue(config.removeForumAutoTagConfig("1"));
        assertFalse(config.removeForumAutoTagConfig("1"));

        assertNull(config.getForumAutoTagConfig("1"));
        assertEquals("Submitted", config.getForumAutoTagConfig("2").getDefaultTagName());
    }
}
