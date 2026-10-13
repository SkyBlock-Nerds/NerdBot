package net.hypixel.nerdbot.app.user;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/** Announces birthdays hourly, so each one goes out on the day in the member's own time zone. */
@Slf4j
public class BirthdayAnnouncementFeature extends BotFeature {

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration PERIOD = Duration.ofHours(1);

    private final DiscordUserStore users;
    private final GuildScope guildScope;
    private final JDA jda;
    private final Provider<NerdBotConfig> config;
    private final Provider<BotState> state;
    private final Clock clock;

    @Inject
    public BirthdayAnnouncementFeature(DiscordUserStore users, GuildScope guildScope, JDA jda, Provider<NerdBotConfig> config,
                                       Provider<BotState> state, Clock clock) {
        this.users = users;
        this.guildScope = guildScope;
        this.jda = jda;
        this.config = config;
        this.state = state;
        this.clock = clock;
    }

    @Override
    public void onFeatureStart() {
        if (state.get().isReadOnly()) {
            log.warn("Not announcing birthdays; the bot is in read-only mode");
            return;
        }

        scheduleAtFixedRate("birthday-announcement-task", this::announceDueBirthdays, INITIAL_DELAY.toMillis(), PERIOD.toMillis());
    }

    void announceDueBirthdays() {
        String channelId = config.get().getChannelConfig().getBirthdayNotificationChannelId();
        // JDA throws on an id that isn't a number, which a blank config value would be
        Optional<TextChannel> channel = Optional.ofNullable(channelId)
            .filter(id -> id.matches("\\d+"))
            .map(jda::getTextChannelById);
        Optional<Guild> guild = guildScope.mainGuild(jda);

        // Checked up front so nobody gets marked as announced while there is nowhere to post
        if (channel.isEmpty() || guild.isEmpty()) {
            log.warn("Not announcing birthdays; the birthday channel ({}) or the guild can't be found", channelId);
            return;
        }

        BirthdayAnnouncer announcer = new BirthdayAnnouncer(users, message -> channel.get().sendMessage(message).submit(), clock,
            memberId -> guild.get().getMemberById(memberId) != null);
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
