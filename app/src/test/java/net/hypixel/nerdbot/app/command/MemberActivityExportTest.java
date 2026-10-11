package net.hypixel.nerdbot.app.command;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberActivityExportTest {

    private static final String THIS_MONTH = YearMonth.now().format(DateTimeFormatter.ofPattern("MM-yyyy"));

    @Test
    void everyRowHasOneCellPerHeader() {
        List<String> headers = ExportCommands.memberActivityHeaders(30);
        List<String> row = ExportCommands.memberActivityRow("nerd", new DiscordUser("1"), 30);

        assertEquals(headers.size(), row.size());
    }

    @Test
    void reviewedAndCommentsColumnsLineUp() {
        List<String> headers = ExportCommands.memberActivityHeaders(30);
        List<String> row = ExportCommands.memberActivityRow("nerd", new DiscordUser("1"), 30);

        assertEquals("FALSE", row.get(headers.indexOf("Reviewed")));
        assertEquals("", row.get(headers.indexOf("Comments")));
    }

    @Test
    void headerNamesTheDayWindow() {
        assertTrue(ExportCommands.memberActivityHeaders(14).contains("Messages Sent (Last 14d)"));
    }

    @Test
    void channelBreakdownIsSortedBusiestFirst() {
        DiscordUser user = userWithChannels(
            entry("general", 3),
            entry("suggestions", 10)
        );

        String breakdown = channelBreakdown(user);

        assertEquals("\"suggestions: 10\ngeneral: 3\"", breakdown);
    }

    @Test
    void quotesInChannelNamesAreEscapedForCsv() {
        DiscordUser user = userWithChannels(entry("the \"best\" channel", 1));

        assertEquals("\"the \"\"best\"\" channel: 1\"", channelBreakdown(user));
    }

    @Test
    void noChannelActivityShowsNotApplicable() {
        assertEquals("\"N/A\"", channelBreakdown(new DiscordUser("1")));
    }

    private static String channelBreakdown(DiscordUser user) {
        List<String> headers = ExportCommands.memberActivityHeaders(30);
        return ExportCommands.memberActivityRow("nerd", user, 30).get(headers.indexOf("Messages Sent (Last 30d)"));
    }

    private static DiscordUser userWithChannels(ChannelActivityEntry... entries) {
        DiscordUser user = new DiscordUser("1");
        user.getLastActivity().getChannelActivityHistory().addAll(List.of(entries));
        return user;
    }

    private static ChannelActivityEntry entry(String channelName, int messages) {
        Map<String, Integer> monthly = new HashMap<>(Map.of(THIS_MONTH, messages));
        return new ChannelActivityEntry(channelName + "-id", channelName, messages, System.currentTimeMillis(), monthly);
    }
}
