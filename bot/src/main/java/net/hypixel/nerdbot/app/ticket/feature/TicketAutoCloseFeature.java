package net.hypixel.nerdbot.app.ticket.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;

import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Periodic feature that scans for inactive tickets and automatically closes them
 * once they have exceeded the configured inactivity window.
 */
@Slf4j
public class TicketAutoCloseFeature extends BotFeature implements SchedulableFeature {

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        return TimeUnit.HOURS.toMillis(1);
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    TicketService.getInstance().closeStaleTickets();
                } catch (Exception e) {
                    log.error("Error running ticket auto-close task", e);
                }
            }
        };
    }

    @Override
    public void onFeatureStart() {
        log.info("Ticket auto-close feature started");
    }

    @Override
    public void onFeatureEnd() {
        log.info("Ticket auto-close feature stopped");
    }
}