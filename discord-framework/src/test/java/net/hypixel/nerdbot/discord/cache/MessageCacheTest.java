package net.hypixel.nerdbot.discord.cache;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessageCacheTest {

    private static final JDA JDA = Fakes.of(JDA.class, Map.of());

    private final MessageCache cache = new MessageCache();

    private static Message message(String id) {
        return Fakes.of(Message.class, Map.of("getId", id));
    }

    private void receive(Message message) {
        cache.onMessageReceived(new MessageReceivedEvent(JDA, 0, message));
    }

    @Test
    void receivedMessagesAreCached() {
        Message message = message("1");

        receive(message);

        assertSame(message, cache.get("1").orElseThrow());
    }

    @Test
    void removingHandsBackTheMessageOnce() {
        Message message = message("1");
        receive(message);

        assertSame(message, cache.remove("1").orElseThrow());
        assertEquals(Optional.empty(), cache.remove("1"));
    }

    @Test
    void replacingHandsBackTheCopyFromBeforeTheEdit() {
        Message before = message("1");
        Message after = message("1");
        receive(before);

        assertSame(before, cache.replace(after).orElseThrow());
        assertSame(after, cache.get("1").orElseThrow());
    }

    @Test
    void anEditToAMessageNeverSeenIsNotCached() {
        assertEquals(Optional.empty(), cache.replace(message("1")));
        assertEquals(Optional.empty(), cache.get("1"));
    }

    @Test
    void aBulkDeleteDropsEveryMessageInIt() {
        receive(message("1"));
        receive(message("2"));
        receive(message("3"));

        cache.onMessageBulkDelete(new MessageBulkDeleteEvent(JDA, 0, Fakes.of(GuildMessageChannel.class, Map.of()), List.of("1", "2")));

        assertEquals(Optional.empty(), cache.get("1"));
        assertEquals(Optional.empty(), cache.get("2"));
        assertEquals("3", cache.get("3").orElseThrow().getId());
    }
}
