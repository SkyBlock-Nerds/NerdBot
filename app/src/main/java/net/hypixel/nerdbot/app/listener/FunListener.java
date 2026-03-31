package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.discord.config.FunConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

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

        ZoneId zoneId = funConfig.getAprilFoolsTimezone() != null
            ? ZoneId.of(funConfig.getAprilFoolsTimezone())
            : ZoneId.systemDefault();
        LocalDate now = LocalDate.now(zoneId);
        boolean isAprilFirst = now.getMonth() == Month.APRIL && now.getDayOfMonth() == 1;

        if (isAprilFirst
            && funConfig.getAprilFoolsReactionChance() != null
            && funConfig.getAprilFoolsReactionEmoji() != null
            && ThreadLocalRandom.current().nextDouble() < funConfig.getAprilFoolsReactionChance()) {
            DiscordUtils.getEmoji(funConfig.getAprilFoolsReactionEmoji())
                .ifPresent(emoji -> event.getMessage().addReaction(emoji).queue());
        }
    }
}
