package net.hypixel.nerdbot.app.activity;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.app.storage.RepositoryDiscordUserStore;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Slf4j
public class ActivityPurgeFeature extends BotFeature implements SchedulableFeature {

    @Override
    public void onFeatureStart() {
    }

    @Override
    public void executeTask() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.warn("Bot is in read-only mode, skipping activity purge task!");
            return;
        }

        DiscordUserRepository discordUserRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        int daysRequiredForVoteHistory = SkyBlockNerdsBot.config().getRoleConfig().getDaysRequiredForVoteHistory();

        int purged = purgeOldHistory(new RepositoryDiscordUserStore(discordUserRepository), daysRequiredForVoteHistory);
        log.debug("Activity purge complete: {} user(s) had stale history removed", purged);
    }

    /**
     * Purge activity history older than {@code daysRequiredForVoteHistory} for every stored user,
     * re-saving only the users whose history actually changed.
     *
     * <p>Extracted from {@link #executeTask()} so the purge logic can be exercised against an
     * in-memory {@link DiscordUserStore} without a live bot or database. {@code executeTask()}
     * remains the thin adapter that supplies the real store and configured retention window.
     *
     * @param store                       the user store to purge in place
     * @param daysRequiredForVoteHistory  retention window in days; entries at or older than this
     *                                    are removed
     * @return the number of users whose history was modified
     */
    static int purgeOldHistory(DiscordUserStore store, int daysRequiredForVoteHistory) {
        int purged = 0;

        for (DiscordUser discordUser : store.getAll()) {
            if (discordUser.getLastActivity().purgeOldHistory(daysRequiredForVoteHistory)) {
                store.save(discordUser);
                purged++;
            }
        }

        return purged;
    }

    @Override
    public long defaultInitialDelayMs(DiscordBotConfig config) {
        return Duration.of(((NerdBotConfig) config).getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
    }

    @Override
    public long defaultPeriodMs(DiscordBotConfig config) {
        return Duration.of(((NerdBotConfig) config).getMojangUsernameCacheTTL(), ChronoUnit.HOURS).toMillis();
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}
