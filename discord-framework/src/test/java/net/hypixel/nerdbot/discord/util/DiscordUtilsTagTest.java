package net.hypixel.nerdbot.discord.util;

import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordUtilsTagTest {

    private static final ForumTag GREENLIT = tag("Greenlit");
    private static final ForumTag REVIEWED = tag("Reviewed");
    private static final ForumChannel FORUM = Fakes.of(ForumChannel.class, Map.of("getName", "suggestions", "getAvailableTags", List.of(GREENLIT, REVIEWED)));
    private static final ThreadChannel THREAD = Fakes.of(ThreadChannel.class, Map.of("getAppliedTags", List.of(REVIEWED)));

    private static ForumTag tag(String name) {
        return Fakes.of(ForumTag.class, Map.of("getName", name));
    }

    @Test
    void findsTagsIgnoringCaseByDefault() {
        assertSame(GREENLIT, DiscordUtils.findTagByName(FORUM, "greenlit").orElseThrow());
        assertSame(GREENLIT, DiscordUtils.getTagByName(FORUM, "GREENLIT"));
    }

    @Test
    void caseSensitiveLookupsNeedAnExactMatch() {
        assertTrue(DiscordUtils.findTagByName(FORUM, "greenlit", false).isEmpty());
        assertFalse(DiscordUtils.hasTagByName(FORUM, "greenlit", false));
        assertTrue(DiscordUtils.hasTagByName(FORUM, "Greenlit", false));
    }

    @Test
    void aMissingTagIsEmptyForFindAndAnErrorNamingItForGet() {
        assertTrue(DiscordUtils.findTagByName(FORUM, "Denied").isEmpty());

        NoSuchElementException failure = assertThrows(NoSuchElementException.class, () -> DiscordUtils.getTagByName(FORUM, "Denied"));
        assertTrue(failure.getMessage().contains("Denied"), failure.getMessage());
    }

    @Test
    void threadTagChecksLookAtAppliedTagsOnly() {
        assertTrue(DiscordUtils.hasTagByName(THREAD, "reviewed"));
        assertFalse(DiscordUtils.hasTagByName(THREAD, "greenlit"));
        assertFalse(DiscordUtils.hasTagByName(THREAD, "Reviewed ", false));
    }
}
