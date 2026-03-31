package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.config.FunConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.marmalade.format.TimeUtils;

import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ThreadLocalRandom;

@Log4j2
public class FunListener {

    @SubscribeEvent
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        FunConfig funConfig = DiscordBotEnvironment.getBot().getConfig().getFunConfig();

        funConfig.getAutoReactions().forEach((userId, emojiValue) -> {
            if (event.getAuthor().getId().equals(userId)) {
                DiscordUtils.getEmoji(emojiValue).ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
            }
        });

        log.debug("April Fools: isAprilFirst={}, chance={}, emoji={}",
            TimeUtils.isAprilFirst(), funConfig.getAprilFoolsReactionChance(), funConfig.getAprilFoolsReactionEmoji());

        if (TimeUtils.isAprilFirst()
            && funConfig.getAprilFoolsReactionChance() != null
            && funConfig.getAprilFoolsReactionEmoji() != null
            && ThreadLocalRandom.current().nextDouble() < funConfig.getAprilFoolsReactionChance()) {
            DiscordUtils.getEmoji(funConfig.getAprilFoolsReactionEmoji())
                .ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
        }
    }
}
