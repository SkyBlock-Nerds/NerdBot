package net.hypixel.nerdbot.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.channel.ChannelConfig;
import net.hypixel.nerdbot.util.ArrayUtils;

@Slf4j
public class PinListener {

    private final Emoji pushpin = Emoji.fromUnicode("\uD83D\uDCCC");
    private final Emoji roundPushpin = Emoji.fromUnicode("\uD83D\uDCCD");

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return; // Ignore Non Guild
        }

        Member member = event.getMember();
        if (member == null) {
            return; // Ignore Empty Member
        }

        if (member.getUser().isBot()) {
            // This deletes automated pinned messages sent by the bot.
            if (event.getMessage().getType() == MessageType.CHANNEL_PINNED_ADD) {
                event.getMessage().delete().complete();
            }
            return; // Ignore any other bot messages
        }

        if (event.getChannelType() != net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
            return; // Ignore any channels that are not this type.
        }

        if (!event.getChannel().getId().equals(event.getMessage().getId())) {
            return; // Ignore any message that the thread and message IDs do not match.
        }

        // Ignore if feature is globally turned off.
        ChannelConfig channelConfig = NerdBotApp.getBot().getConfig().getChannelConfig();
        if (!channelConfig.isAutoPinFirstMessage()) {
            return;
        }

        // Ignore channel if blacklisted.
        String[] autoPinBlacklistedChannels = channelConfig.getAutoPinBlacklistedChannels();
        String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().asForumChannel().getId();
        if (ArrayUtils.safeArrayStream(autoPinBlacklistedChannels).anyMatch(forumChannelId::equalsIgnoreCase)) {
            return;
        }

        event.getMessage().pin().complete();
    }

    @SubscribeEvent
    public void onReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild()) {
            return; // Ignore Non Guild
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        if (event.getGuildChannel() instanceof ThreadChannel threadChannel) {
            if (!threadChannel.getOwnerId().equals(member.getId())) {
                return; // Ignoring IDs that are not from the owner of the thread.
            }

            // Here is logic for handling pinning of messages within Threads by the owner. This is done by either emoji.
            Emoji reactionEmoji = event.getReaction().getEmoji();
            if (!(reactionEmoji.equals(pushpin) || reactionEmoji.equals(roundPushpin))) {
                return; // Ignoring any emojis that are NOT in this list.
            }

            // We get the message we are checking to pin it.
            Message message = event.retrieveMessage().complete();
            if (!message.isPinned()) {
                // We pin the message here.
                message.pin().complete();
            }
        }
    }

    @SubscribeEvent
    public void onReactionRemove(MessageReactionRemoveEvent event) {
        if (!event.isFromGuild()) {
            return; // Ignore Non Guild
        }

        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) {
            return; // Ignore Empty Member
        }

        if (event.getGuildChannel() instanceof ThreadChannel threadChannel) {
            if (!threadChannel.getOwnerId().equals(member.getId())) {
                return; // Ignoring IDs that are not from the owner of the thread.
            }

            // Here is logic for handling pinning of messages within Threads by the owner. This is done by either emoji.
            Emoji reactionEmoji = event.getReaction().getEmoji();
            if (!(reactionEmoji.equals(pushpin) || reactionEmoji.equals(roundPushpin))) {
                return; // Ignoring any emojis that are NOT in this list.
            }

            // We get the message we are checking to unpin it.
            Message message = event.retrieveMessage().complete();
            if (message.isPinned()) {
                // We unpin the message here.
                message.unpin().complete();
            }
        }
    }
}
