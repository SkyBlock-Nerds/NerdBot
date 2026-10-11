package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeAutocompleteTest {

    private static final Map<String, Badge> BADGES = Map.of(
        "early_supporter", new Badge("early_supporter", "Early Supporter", "<:a:1>"),
        "bug_hunter", new Badge("bug_hunter", "Bug Hunter", "<:b:2>"),
        "event_winner", new Badge("event_winner", "Event Winner", "<:c:3>")
    );

    @Test
    void filtersOnWhatTheUserTyped() {
        assertEquals(List.of("Bug Hunter"), names(BadgeCommands.matchingBadgeChoices(BADGES, "bug")));
    }

    @Test
    void matchingIgnoresCase() {
        assertEquals(List.of("Event Winner"), names(BadgeCommands.matchingBadgeChoices(BADGES, "WINNER")));
    }

    @Test
    void choiceValueIsTheBadgeId() {
        Command.Choice choice = BadgeCommands.matchingBadgeChoices(BADGES, "early").getFirst();

        assertEquals("early_supporter", choice.getAsString());
    }

    @Test
    void emptyQueryListsEveryBadgeSortedByName() {
        assertEquals(List.of("Bug Hunter", "Early Supporter", "Event Winner"), names(BadgeCommands.matchingBadgeChoices(BADGES, "")));
    }

    @Test
    void neverReturnsMoreChoicesThanDiscordAllows() {
        Map<String, Badge> manyBadges = new LinkedHashMap<>();
        for (int i = 0; i < OptionData.MAX_CHOICES + 10; i++) {
            manyBadges.put("badge_" + i, new Badge("badge_" + i, "Badge " + i, "<:x:" + i + ">"));
        }

        assertEquals(OptionData.MAX_CHOICES, BadgeCommands.matchingBadgeChoices(manyBadges, "").size());
    }

    @Test
    void noMatchGivesNoChoices() {
        assertTrue(BadgeCommands.matchingBadgeChoices(BADGES, "nothing like this").isEmpty());
    }

    private static List<String> names(List<Command.Choice> choices) {
        return choices.stream().map(Command.Choice::getName).toList();
    }
}
