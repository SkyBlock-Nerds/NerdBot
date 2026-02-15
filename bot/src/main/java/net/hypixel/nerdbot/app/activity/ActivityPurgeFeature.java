package net.hypixel.nerdbot.app.activity;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.bot.DiscordBot;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.config.FeatureConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Slf4j
public class ActivityPurgeFeature extends BotFeature implements SchedulableFeature {

    @Override
    public void onFeatureStart() {
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (BotEnvironment.getBot().isReadOnly()) {
                    log.warn("Bot is in read-only mode, skipping activity purge task!");
                    return;
                }

                DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
                int daysRequiredForVoteHistory = SkyBlockNerdsBot.config().getRoleConfig().getDaysRequiredForVoteHistory();
                discordUserRepository.getAll().forEach(discordUser -> {
                    if (discordUser.getLastActivity().purgeOldHistory(daysRequiredForVoteHistory)) {
                        discordUserRepository.cacheObject(discordUser);
                    }
                });
            }
        };
    }

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return Duration.of(config.getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        return Duration.of(config.getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
    }

    @Override
    public void onFeatureEnd() {
        this.timer.cancel();
    }
}
