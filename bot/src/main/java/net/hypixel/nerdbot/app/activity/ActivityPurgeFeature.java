package net.hypixel.nerdbot.app.activity;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Slf4j
public class ActivityPurgeFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        DiscordBot discordBot = BotEnvironment.getBot(DiscordBot.class);
        long oneHour = Duration.of(SkyBlockNerdsBot.config().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
        int daysRequiredForVoteHistory = SkyBlockNerdsBot.config().getRoleConfig().getDaysRequiredForVoteHistory();

        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    if (BotEnvironment.getBot().isReadOnly()) {
                        log.warn("Bot is in read-only mode, skipping profile update task!");
                        return;
                    }

                    DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                    discordUserRepository.getAll().forEach(discordUser -> {
                        if (discordUser.getLastActivity().purgeOldHistory(daysRequiredForVoteHistory)) {
                            discordUserRepository.cacheObject(discordUser);
                        }
                    });
                }
            }, oneHour, oneHour);
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
