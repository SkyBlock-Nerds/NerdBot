package net.hypixel.nerdbot.app.command.util;

import net.hypixel.nerdbot.app.command.util.SuggestionCommandUtils.TagFilters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link SuggestionCommandUtils#parseTagFilters}, the include/exclude tag filter parsing.
 */
class SuggestionCommandUtilsTest {

    @Test
    void separatesIncludeAndExcludeTags() {
        TagFilters filters = SuggestionCommandUtils.parseTagFilters("bug, !wontfix");

        assertEquals(List.of("bug"), filters.include());
        assertEquals(List.of("wontfix"), filters.exclude());
    }

    @Test
    void treatsAllTagsAsIncludesWhenNoneAreNegated() {
        TagFilters filters = SuggestionCommandUtils.parseTagFilters("bug, feature");

        assertEquals(List.of("bug", "feature"), filters.include());
        assertEquals(List.of(), filters.exclude());
    }

    @Test
    void stripsTheBangFromEveryExcludedTag() {
        TagFilters filters = SuggestionCommandUtils.parseTagFilters("!a, !b");

        assertEquals(List.of(), filters.include());
        assertEquals(List.of("a", "b"), filters.exclude());
    }

    @Test
    void yieldsEmptyListsForNullOrBlank() {
        assertEquals(List.of(), SuggestionCommandUtils.parseTagFilters(null).include());
        assertEquals(List.of(), SuggestionCommandUtils.parseTagFilters("   ").include());
        assertEquals(List.of(), SuggestionCommandUtils.parseTagFilters(null).exclude());
    }

    @Test
    void splitsWithOrWithoutASpaceAfterTheComma() {
        assertEquals(List.of("a", "b"), SuggestionCommandUtils.parseTagFilters("a,b").include());
        assertEquals(List.of("a", "b"), SuggestionCommandUtils.parseTagFilters("a, b").include());
    }
}
