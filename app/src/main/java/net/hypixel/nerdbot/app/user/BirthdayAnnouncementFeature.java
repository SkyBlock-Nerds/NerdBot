package net.hypixel.nerdbot.app.user;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.storage.RepositoryDiscordUserStore;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.repository.DiscordUserRepository;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BirthdayAnnouncementFeature extends BotFeature {

    private static final long INITIAL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
    private static final long PERIOD_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    public void onFeatureStart() {
        if (BotEnvironment.getBot().isReadOnly()) {
            log.warn("Bot is in read-only mode, skipping birthday announcements!");
            return;
        }

        scheduleAtFixedRate("birthday-announcement-task", this::announceDueBirthdays, INITIAL_DELAY_MS, PERIOD_MS);
    }

    private void announceDueBirthdays() {
        if (!BotEnvironment.getBot().getDatabase().isConnected()) {
            log.error("Skipping birthday announcements as the database is not connected!");
            return;
        }

        String channelId = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getBirthdayNotificationChannelId();
        Optional<TextChannel> channel = channelId == null || channelId.isBlank()
            ? Optional.empty() : ChannelCache.getTextChannelById(channelId);

        // Checked up front so nobody gets marked as announced while there is nowhere to post
        if (channel.isEmpty()) {
            log.warn("Birthday channel '{}' not found, skipping birthday announcements", channelId);
            return;
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        BirthdayAnnouncer announcer = new BirthdayAnnouncer(new RepositoryDiscordUserStore(repository),
            message -> channel.get().sendMessage(message).submit(), Clock.systemUTC());

        int started = announcer.announceDue();
        if (started > 0) {
            log.info("Started {} birthday announcement(s)", started);
        }
    }

    @Override
    public void onFeatureEnd() {
        stopScheduledTask();
    }
}