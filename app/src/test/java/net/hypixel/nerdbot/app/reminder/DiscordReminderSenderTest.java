package net.hypixel.nerdbot.app.reminder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordReminderSenderTest {

    private static final long TIME = 1_800_000_000_000L;

    @Test
    void onTimeMessage() {
        assertEquals("<@1>, you asked me to remind you at <t:1800000000:F> about: ",
            DiscordReminderSender.dueMessage("<@1>", TIME, false));
    }

    @Test
    void lateMessageSaysTheBotWasOffline() {
        assertEquals("<@1>, while I was offline, you asked me to remind you at <t:1800000000:F> about: ",
            DiscordReminderSender.dueMessage("<@1>", TIME, true));
    }
}
