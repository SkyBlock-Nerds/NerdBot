package net.hypixel.nerdbot.app.ticket;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.channel.TicketConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Periodically checks open tickets and sends
 * reminder pings based on the configured thresholds.
 */
@Slf4j
public class TicketReminderFeature extends BotFeature implements SchedulableFeature {

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return TimeUnit.MINUTES.toMillis(2);
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        TicketConfig ticketConfig = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        int intervalMinutes = ticketConfig.getReminderCheckIntervalMinutes();

        return intervalMinutes > 0
            ? TimeUnit.MINUTES.toMillis(intervalMinutes)
            : TimeUnit.MINUTES.toMillis(30);
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
                    if (!config.isRemindersEnabled()) {
                        return;
                    }

                    TicketService.getInstance().sendReminders();
                } catch (Exception e) {
                    log.error("Error running ticket reminder task", e);
                }
            }
        };
    }

    @Override
    public void onFeatureStart() {
        TicketConfig config = DiscordBotEnvironment.getBot().getConfig().getTicketConfig();
        if (config.isRemindersEnabled()) {
            log.info("Ticket reminder feature started (check interval: {} minutes)",
                config.getReminderCheckIntervalMinutes());
        } else {
            log.info("Ticket reminder feature started but reminders are disabled");
        }
    }

    @Override
    public void onFeatureEnd() {
        log.info("Ticket reminder feature stopped");
    }
}