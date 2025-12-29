package net.hypixel.nerdbot.app.ticket.feature;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.ticket.service.TicketService;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.api.feature.SchedulableFeature;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;

import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Periodic feature that deletes old closed tickets that have exceeded
 * the configured retention period. Both the Discord thread and MongoDB
 * record are removed.
 */
@Slf4j
public class TicketCleanupFeature extends BotFeature implements SchedulableFeature {

    @Override
    public long defaultInitialDelayMs(NerdBotConfig config) {
        return TimeUnit.MINUTES.toMillis(5);
    }

    @Override
    public long defaultPeriodMs(NerdBotConfig config) {
        return TimeUnit.DAYS.toMillis(1);
    }

    @Override
    public TimerTask buildTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    TicketService.getInstance().deleteOldClosedTickets();
                } catch (Exception e) {
                    log.error("Error running ticket cleanup task", e);
                }
            }
        };
    }

    @Override
    public void onFeatureStart() {
        log.info("Ticket cleanup feature started");
    }

    @Override
    public void onFeatureEnd() {
        log.info("Ticket cleanup feature stopped");
    }
}