package net.hypixel.nerdbot.discord.user;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.BotEnvironment;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.cache.ChannelCache;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;

@UtilityClass
@Slf4j
public class BirthdayScheduler {

    public void schedule(DiscordUser discordUser) {
        BirthdayData birthdayData = discordUser.getBirthdayData();

        if (birthdayData == null || !birthdayData.isBirthdaySet()) {
            return;
        }

        Date nextBirthday = birthdayData.getBirthdayThisYear();
        if (nextBirthday == null) {
            return;
        }

        if (birthdayData.getTimer() != null) {
            birthdayData.getTimer().cancel();
        }

        Timer timer = new Timer();
        birthdayData.setTimer(timer);
        timer.schedule(new BirthdayTask(discordUser, nextBirthday), nextBirthday);
    }

    private static class BirthdayTask extends TimerTask {

        private final DiscordUser user;
        private final Date date;

        private BirthdayTask(DiscordUser user, Date date) {
            this.user = user;
            this.date = date;
        }

        @Override
        public void run() {
            String channelId = DiscordBotEnvironment.getBot().getConfig().getChannelConfig().getBirthdayNotificationChannelId();
            BirthdayData birthdayData = user.getBirthdayData();

            if (channelId != null && !channelId.isBlank()) {
                ChannelCache.getTextChannelById(channelId).ifPresentOrElse(channel -> {
                    String message = "Happy birthday <@%s>!";
                    if (birthdayData.isShouldAnnounceAge()) {
                        message += " You are now %d years old!";
                    }
                    channel.sendMessage(String.format(message, user.getDiscordId(), birthdayData.getAge())).queue();
                    log.info("Sent birthday message for {} at {}", user.getDiscordId(), date);
                }, () -> log.warn("Cannot find channel to send birthday message for {}", user.getDiscordId()));
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.YEAR, 1);
            Date nextDate = calendar.getTime();
            user.getBirthdayData().setBirthdayTimestamp(user.getBirthdayData().getBirthdayTimestamp());
            BirthdayScheduler.schedule(user); // reschedule for next year
            log.debug("Scheduled next birthday reminder for {} at {}", user.getDiscordId(), nextDate);
        }
    }
}
