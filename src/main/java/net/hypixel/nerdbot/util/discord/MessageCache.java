package net.hypixel.nerdbot.util.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.internal.requests.CompletedRestAction;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class MessageCache implements EventListener {

    private final Cache<String, Message> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(7, TimeUnit.DAYS)
        .build();

    public MessageCache() {
        NerdBotApp.getBot().getJDA().addEventListener(this);
    }

    public RestAction<Message> getMessage(MessageChannel channel, String messageId) {
        Message message = getMessage(messageId);
        return message == null ? channel.retrieveMessageById(messageId) : new CompletedRestAction<>(NerdBotApp.getBot().getJDA(), message);
    }

    public Message getMessage(String id) {
        return cache.asMap().get(id);
    }

    @SubscribeEvent
    public void onEvent(@NotNull GenericEvent event) {

        if (event instanceof MessageReceivedEvent messageReceivedEvent) {
            cache.put(messageReceivedEvent.getMessage().getId(), messageReceivedEvent.getMessage());
        }

        if (event instanceof MessageUpdateEvent messageUpdateEvent) {
            cache.put(messageUpdateEvent.getMessage().getId(), messageUpdateEvent.getMessage());
        }

        if (event instanceof MessageDeleteEvent messageDeleteEvent) {
            cache.asMap().remove(messageDeleteEvent.getMessageId());
        }

        if (event instanceof MessageBulkDeleteEvent messageBulkDeleteEvent) {
            messageBulkDeleteEvent.getMessageIds().forEach(cache.asMap().keySet()::remove);
        }
    }
}
