package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.DiscordUtils;

public class FunListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        NerdBotApp.getBot().getConfig().getFunConfig().getAutoReactions().forEach((s, s2) -> {
            if (event.getAuthor().getId().equals(s)) {
                DiscordUtils.getEmoji(s2).ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
            }
        });
    }
}
