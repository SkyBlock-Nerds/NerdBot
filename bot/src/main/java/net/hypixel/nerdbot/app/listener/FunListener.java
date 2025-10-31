package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

public class FunListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        DiscordBotEnvironment.getBot().getConfig().getFunConfig().getAutoReactions().forEach((s, s2) -> {
            if (event.getAuthor().getId().equals(s)) {
                DiscordUtils.getEmoji(s2).ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
            }
        });
    }
}
