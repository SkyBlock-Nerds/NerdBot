package net.hypixel.nerdbot.feature;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.internalapi.feature.BotFeature;
import net.hypixel.nerdbot.repository.DiscordUserRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

@Log4j2
public class ActivityPurgeFeature extends BotFeature {

    @Override
    public void onFeatureStart() {
        long oneHour = Duration.of(NerdBotApp.getBot().getConfig().getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
        this.timer.scheduleAtFixedRate(
            new TimerTask() {
                @Override
                public void run() {
                    if (NerdBotApp.getBot().isReadOnly()) {
                        log.warn("Bot is in read-only mode, skipping profile update task!");
                        return;
                    }

                    DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
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
