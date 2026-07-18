package net.hypixel.nerdbot.app.post;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MarkdownPostService#render}, the pure Markdown -> Discord-chunk conversion.
 */
class MarkdownPostServiceTest {

    private static final int DISCORD_MESSAGE_LIMIT = 2_000;

    private final MarkdownPostService service = new MarkdownPostService();

    @Test
    void stripsHtmlTags() {
        List<String> sections = service.render("<b>bold</b> and <i>italic</i> text");

        assertEquals(1, sections.size());
        assertEquals("bold and italic text", sections.getFirst().strip());
    }

    @Test
    void foldsDeepHeadingsToBold() {
        assertEquals("**My Heading**", service.render("#### My Heading").getFirst().strip());
    }

    @Test
    void removesHorizontalRules() {
        String section = service.render("before\n\n---\n\nafter").getFirst();

        assertFalse(section.contains("---"), "the horizontal rule should be removed");
        assertTrue(section.contains("before") && section.contains("after"), "surrounding content should remain");
    }

    @Test
    void rendersMarkdownTableAsFixedWidthCodeBlock() {
        String section = service.render("| Name | Age |\n|------|-----|\n| Bob | 30 |\n").getFirst();

        assertTrue(section.contains("```"), "table should be wrapped in a code block");
        assertTrue(section.contains("+"), "table should have a fixed-width border");
        assertTrue(section.contains("Name") && section.contains("Bob") && section.contains("30"),
            "table cell values should survive");
    }

    @Test
    void keepsShortContentAsASingleSection() {
        List<String> sections = service.render("short text");

        assertEquals(1, sections.size());
        assertEquals("short text", sections.getFirst().strip());
    }

    @Test
    void splitsOversizedContentIntoLimitCompliantSections() {
        String paragraph = "x".repeat(800);
        String markdown = paragraph + "\n\n" + paragraph + "\n\n" + paragraph; // ~2400 chars, three paragraphs

        List<String> sections = service.render(markdown);

        assertTrue(sections.size() > 1, "oversized content should be split");
        assertTrue(sections.stream().allMatch(section -> section.length() <= DISCORD_MESSAGE_LIMIT),
            "every section must fit within Discord's message limit");
    }

    @Test
    void hardSplitsASingleOverlongLine() {
        List<String> sections = service.render("y".repeat(2_500));

        assertTrue(sections.size() > 1, "a single overlong line must be hard-split");
        assertTrue(sections.stream().allMatch(section -> section.length() <= DISCORD_MESSAGE_LIMIT),
            "every chunk must fit within Discord's message limit");
    }
}
