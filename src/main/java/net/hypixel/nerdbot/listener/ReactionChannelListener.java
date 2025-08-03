package net.hypixel.nerdbot.listener;

import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.bot.config.objects.ReactionChannel;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ReactionChannelListener {

    @SubscribeEvent
    public void onMessageReceive(MessageReceivedEvent event) {
        List<ReactionChannel> reactionChannels = NerdBotApp.getBot().getConfig().getChannelConfig().getReactionChannels();

        if (reactionChannels == null) {
            return;
        }

        Optional<ReactionChannel> reactionChannel = reactionChannels.stream()
            .filter(channel -> channel.getDiscordChannelId().equals(event.getChannel().getId()))
            .findFirst();

        if (reactionChannel.isPresent()) {
            Message message = event.getMessage();

            reactionChannel.get().getEmojis()
                .forEach(emoji -> {
                    message.addReaction(emoji).queue();
                    log.info("[Reaction Channel] Added reaction '" + emoji.getName() + "' to message " + message.getId() + " in reaction channel " + reactionChannel.get().getName());
                });

            if (reactionChannel.get().isThread()) {
                message.createThreadChannel("Related messages here").queue(thread -> {
                    log.info("[Reaction Channel] Created thread for message " + message.getId() + " in reaction channel " + reactionChannel.get().getName());
                });
            }
            return;
        }

        String pollChannelId = NerdBotApp.getBot().getConfig().getChannelConfig().getPollChannelId();

        if (event.getChannel().getId().equalsIgnoreCase(pollChannelId)) {
            EmojiParser.extractEmojis(event.getMessage().getContentRaw()).stream()
                .map(Emoji::fromUnicode)
                .forEach(emoji -> {
                    event.getMessage().addReaction(emoji).queue();
                    log.info("[Polls] [" + pollChannelId + "] Added reaction '" + emoji.getName() + "' to message " + event.getMessage().getId());
                });
        }
    }
}
