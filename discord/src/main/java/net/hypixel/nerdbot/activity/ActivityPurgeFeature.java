package net.hypixel.nerdbot.activity;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Slf4j
public class ActivityPurgeFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        long oneHour = Duration.of(BotEnvironment.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
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
                        if (discordUser.getLastActivity().purgeOldHistory()) {
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