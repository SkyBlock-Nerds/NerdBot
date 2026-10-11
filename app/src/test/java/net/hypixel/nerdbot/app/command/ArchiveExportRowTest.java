package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveExportRowTest {

    private static final User AUTHOR = Fakes.of(User.class, Map.of("getName", "nerd", "getId", "123456789"));

    @Test
    void userIdColumnHoldsTheAuthorIdNotTheirName() {
        List<String> row = ArchiveExporter.buildRow(message("hello"), "42", "Some thread");

        assertEquals("nerd", row.get(ArchiveExporter.CSV_HEADERS.indexOf("Username")));
        assertEquals("123456789", row.get(ArchiveExporter.CSV_HEADERS.indexOf("User ID")));
    }

    @Test
    void rowHasOneCellPerHeader() {
        List<String> row = ArchiveExporter.buildRow(message("hello"), "42", "Some thread");

        assertEquals(ArchiveExporter.CSV_HEADERS.size(), row.size());
    }

    @Test
    void quotesInContentAreEscapedForCsv() {
        List<String> row = ArchiveExporter.buildRow(message("say \"hi\""), "42", "Some thread");

        assertEquals("\"say \"\"hi\"\"\"", row.get(ArchiveExporter.CSV_HEADERS.indexOf("Message Content")));
    }

    private static Message message(String content) {
        return Fakes.of(Message.class, Map.of(
            "getAuthor", AUTHOR,
            "getTimeCreated", OffsetDateTime.of(2026, 10, 11, 12, 0, 0, 0, ZoneOffset.UTC),
            "getId", "999",
            "getContentRaw", content
        ));
    }
}
