package net.hypixel.nerdbot.discord.listener;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.bot.DiscordBot;
import net.hypixel.nerdbot.util.DiscordUtils;

public class FunListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        ((DiscordBot) BotEnvironment.getBot()).getConfig().getFunConfig().getAutoReactions().forEach((s, s2) -> {
            if (event.getAuthor().getId().equals(s)) {
                DiscordUtils.getEmoji(s2).ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
            }
        });
    }
}