package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SweepEmbedsTest {

    private static List<String> tickets(int count) {
        return IntStream.range(0, count).mapToObj(number -> String.format("#%04d: <#%d>", number, 900_000_000_000_000_000L + number)).toList();
    }

    private static MessageEmbed.Field ticketField(List<String> links) {
        EmbedBuilder embed = new EmbedBuilder().setTitle("warning");
        SweepEmbeds.addOpenTickets(embed, links);
        return embed.build().getFields().getFirst();
    }

    @Test
    void ticketsThatFitAreAllListed() {
        assertEquals(String.join("\n", tickets(3)), ticketField(tickets(3)).getValue());
    }

    @Test
    void tooManyTicketsAreCutWithACount() {
        List<String> links = tickets(60);

        String value = ticketField(links).getValue();

        assertTrue(value.length() <= MessageEmbed.VALUE_MAX_LENGTH, "length " + value.length());
        int shown = (int) value.lines().filter(line -> line.startsWith("#")).count();
        assertEquals("...and " + (60 - shown) + " more", value.lines().toList().getLast());
    }

    @Test
    void noTicketsMeansNoField() {
        EmbedBuilder embed = new EmbedBuilder().setTitle("warning");
        SweepEmbeds.addOpenTickets(embed, List.of());

        assertTrue(embed.build().getFields().isEmpty());
    }

    @Test
    void eachRequirementShowsWhetherItsMet() {
        assertEquals("✅ **1,200** / 1,000 required", SweepEmbeds.requirementLine(1_200, 1_000));
        assertEquals("❌ **3** / 5 required", SweepEmbeds.requirementLine(3, 5));
    }
}
