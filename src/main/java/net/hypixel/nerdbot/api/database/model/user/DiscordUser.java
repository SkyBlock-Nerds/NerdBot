package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.channel.ChannelManager;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

@AllArgsConstructor
@Getter
@Setter
@Log4j2
public class DiscordUser {

    private String discordId;
    private LastActivity lastActivity;
    private BirthdayData birthdayData;
    private MojangProfile mojangProfile;

    public DiscordUser() {
    }

    public DiscordUser(Member member) {
        this(member.getId(), new LastActivity(), new BirthdayData(), new MojangProfile());
    }

    public int getTotalMessageCount() {
        return lastActivity.getChannelActivity().values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean isProfileAssigned() {
        return this.mojangProfile != null && this.mojangProfile.getUniqueId() != null;
    }

    public boolean noProfileAssigned() {
        return !this.isProfileAssigned();
    }

    public void scheduleBirthdayReminder(Date date) {
        if (!birthdayData.isBirthdaySet()) {
            throw new IllegalStateException("Cannot schedule birthday reminder when birthday is not set!");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR));
        date = calendar.getTime();

        if (date.before(new Date())) {
            calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR) + 1);
            date = calendar.getTime();
            log.debug("Birthday for " + discordId + " is in the past, scheduling for next year: " + date);
        }

        log.info("Scheduling birthday reminder for " + discordId + " at " + date);

        if (birthdayData.getTimer() != null) {
            log.debug("Canceling previous birthday reminder for " + discordId);
            birthdayData.getTimer().cancel();
        }

        birthdayData.setTimer(new Timer());
        Date finalDate = date;
        birthdayData.getTimer().schedule(new TimerTask() {
            @Override
            public void run() {
                String message = "Happy birthday <@%s>!";

                if (birthdayData.isShouldAnnounceAge()) {
                    message += " You are now %d years old!";
                }

                log.info("Sending birthday message for " + discordId + " at " + finalDate);
                String finalMessage = message;

                ChannelManager.getChannelByName("off-topic").ifPresentOrElse(channel -> {
                    channel.sendMessage(String.format(finalMessage, discordId, birthdayData.getAge())).queue();
                    log.info("Sent birthday message for " + discordId + " at " + finalDate);
                }, () -> {
                    throw new IllegalStateException("Cannot find off-topic channel to send birthday message!");
                });

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(finalDate);
                calendar.add(Calendar.YEAR, 1);
                scheduleBirthdayReminder(calendar.getTime());
            }
        }, date);
    }

    public void setBirthday(Date birthday) {
        if (birthdayData == null) {
            birthdayData = new BirthdayData();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(birthday);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);

        log.info("Setting birthday for " + discordId + " to " + calendar.getTime());

        birthdayData.setBirthday(calendar.getTime());
        birthdayData.setBirthdaySet(true);
    }
}
