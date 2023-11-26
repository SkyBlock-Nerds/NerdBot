package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.channel.ReactionChannel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Log4j2
public class ReactionChannelListener {

    @SubscribeEvent
    public void onMessageReceive(MessageReceivedEvent event) {
        List<ReactionChannel> reactionChannels = NerdBotApp.getBot().getConfig().getChannelConfig().getReactionChannels();

        if (reactionChannels == null) {
            return;
        }

        Optional<ReactionChannel> reactionChannel = reactionChannels.stream().filter(channel -> channel.getDiscordChannelId().equals(event.getChannel().getId())).findFirst();
        if (reactionChannel.isPresent()) {
            Message message = event.getMessage();
            reactionChannel.get().getEmojiIds().stream()
                .map(emojiId -> NerdBotApp.getBot().getJDA().getEmojiById(emojiId))
                .filter(Objects::nonNull)
                .forEachOrdered(emoji -> {
                    message.addReaction(emoji).queue();
                    log.info("Added reaction '" + emoji.getName() + "' to message " + message.getId() + " in reaction channel " + reactionChannel.get().getName());
                });
        }
    }
}
