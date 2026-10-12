package net.hypixel.nerdbot.app.listener;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.marmalade.collections.ArrayUtils;

import java.util.Set;

/**
 * Pins the first message of each new thread, and lets a thread's owner pin or unpin its messages by
 * reacting with a pushpin. Deletes the "pinned a message" notices the bot's own pins leave.
 */
@Slf4j
public class PinListener {

    static final Set<Emoji> PIN_EMOJIS = Set.of(Emoji.fromUnicode("\uD83D\uDCCC"), Emoji.fromUnicode("\uD83D\uDCCD"));

    private final GuildScope guildScope;
    private final Provider<NerdBotConfig> config;

    @Inject
    public PinListener(GuildScope guildScope, Provider<NerdBotConfig> config) {
        this.guildScope = guildScope;
        this.config = config;
    }

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (!event.isFromGuild() || !guildScope.isMainGuild(event.getGuild()) || member == null) {
            return;
        }

        Message message = event.getMessage();
        if (member.getUser().isBot()) {
            if (message.getType() == MessageType.CHANNEL_PINNED_ADD) {
                message.delete().queue(null, error -> log.warn("Failed to delete the pin notice {}", message.getId(), error));
            }
            return;
        }

        if (event.getChannel() instanceof ThreadChannel thread && event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD
            && thread.getId().equals(message.getId()) && shouldPinStarter(thread)) {
            message.pin().queue(null, error -> log.warn("Failed to pin the first message of thread {}", thread.getId(), error));
        }
    }

    boolean shouldPinStarter(ThreadChannel thread) {
        ChannelConfig channelConfig = config.get().getChannelConfig();
        String parentId = thread.getParentChannel().getId();
        return channelConfig.isAutoPinFirstMessage()
            && ArrayUtils.safeArrayStream(channelConfig.getAutoPinBlacklistedChannels()).noneMatch(parentId::equals);
    }

    @SubscribeEvent
    public void onReactionAdd(MessageReactionAddEvent event) {
        ownerReacted(event, true);
    }

    @SubscribeEvent
    public void onReactionRemove(MessageReactionRemoveEvent event) {
        ownerReacted(event, false);
    }

    private void ownerReacted(GenericMessageReactionEvent event, boolean pin) {
        Member member = event.getMember();
        if (!event.isFromGuild() || !guildScope.isMainGuild(event.getGuild()) || member == null || member.getUser().isBot()
            || !(event.getGuildChannel() instanceof ThreadChannel thread) || !thread.getOwnerId().equals(member.getId())
            || !PIN_EMOJIS.contains(event.getEmoji())) {
            return;
        }

        event.retrieveMessage().queue(message -> setPinned(message, pin),
            error -> log.warn("Failed to fetch message {} to {} it", event.getMessageId(), pin ? "pin" : "unpin", error));
    }

    static void setPinned(Message message, boolean pin) {
        if (message.isPinned() == pin) {
            return;
        }

        (pin ? message.pin() : message.unpin()).queue(null,
            error -> log.warn("Failed to {} message {}", pin ? "pin" : "unpin", message.getId(), error));
    }
}
