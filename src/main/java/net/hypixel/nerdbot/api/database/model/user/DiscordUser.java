package net.hypixel.nerdbot.api.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.badge.Badge;
import net.hypixel.nerdbot.api.badge.TieredBadge;
import net.hypixel.nerdbot.api.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.api.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.api.database.model.user.history.GeneratorHistory;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.api.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.util.DiscordUtils;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

@AllArgsConstructor
@Getter
@Setter
@Slf4j
public class DiscordUser {

    private String discordId;
    private List<BadgeEntry> badges;
    private UserLanguage language;
    private LastActivity lastActivity;
    private BirthdayData birthdayData;
    private MojangProfile mojangProfile;
    private GeneratorHistory generatorHistory;
    private boolean autoHideGenCommands;

    public DiscordUser() {
    }

    public DiscordUser(String discordId) {
        this(
            discordId,
            new ArrayList<>(),
            UserLanguage.ENGLISH,
            new LastActivity(),
            new BirthdayData(),
            new MojangProfile(),
            new GeneratorHistory(),
            false
        );
    }

    public DiscordUser(Member member) {
        this(
            member.getId(),
            new ArrayList<>(),
            UserLanguage.ENGLISH,
            new LastActivity(),
            new BirthdayData(),
            new MojangProfile(),
            new GeneratorHistory(),
            false
        );
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

                String channelId = NerdBotApp.getBot().getConfig().getChannelConfig().getBirthdayNotificationChannelId();
                ChannelCache.getTextChannelById(channelId).ifPresentOrElse(channel -> {
                    channel.sendMessage(String.format(finalMessage, discordId, birthdayData.getAge())).queue();
                    log.info("Sent birthday message for " + discordId + " at " + finalDate);
                }, () -> log.warn("Cannot find channel to send birthday message into!"));

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(finalDate);
                calendar.add(Calendar.YEAR, 1);
                scheduleBirthdayReminder(calendar.getTime());
                log.debug("Scheduled next birthday reminder for " + discordId + " at " + calendar.getTime());
            }
        }, date);
    }

    public void setBirthday(Date birthday) {
        if (birthdayData == null) {
            log.debug("Creating new birthday data for " + discordId);
            birthdayData = new BirthdayData();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(birthday);

        if (calendar.get(Calendar.YEAR) > Calendar.getInstance().get(Calendar.YEAR)) {
            throw new DateTimeException("Year cannot be in the future");
        }

        if (calendar.get(Calendar.YEAR) < 1900) {
            throw new DateTimeException("Year cannot be before 1900");
        }

        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);

        log.info("Setting birthday for " + discordId + " to " + calendar.getTime());

        birthdayData.setBirthday(calendar.getTime());
    }

    public boolean addBadge(Badge badge) {
        return badges.add(new BadgeEntry(badge.getId()));
    }

    public boolean addBadge(TieredBadge badge, int tier) {
        badges.removeIf(badgeEntry -> badgeEntry.getBadgeId().equals(badge.getId()));
        log.debug("Removed existing tiered badge for " + discordId + " with ID " + badge.getId() + " and tier " + tier);

        if (tier > 0 && tier <= badge.getTiers().size()) {
            return badges.add(new BadgeEntry(badge.getId(), tier));
        } else {
            throw new IllegalArgumentException("Invalid tier for tiered badge");
        }
    }

    public boolean hasBadge(Badge badge) {
        return badges.stream().map(BadgeEntry::getBadgeId).anyMatch(s -> s.equals(badge.getId()));
    }

    public boolean hasBadge(TieredBadge badge, int tier) {
        return badges.stream().anyMatch(badgeEntry -> badgeEntry.getBadgeId().equals(badge.getId()) && badgeEntry.getTier() == tier);
    }

    public boolean removeBadge(Badge badge) {
        return badges.removeIf(badgeEntry -> badgeEntry.getBadgeId().equals(badge.getId()));
    }

    public boolean removeBadge(TieredBadge badge, int tier) {
        return badges.removeIf(badgeEntry -> badgeEntry.getBadgeId().equals(badge.getId()) && badgeEntry.getTier() == tier);
    }

    public Optional<Member> getMember() {
        return Optional.of(DiscordUtils.getMainGuild().retrieveMemberById(discordId).complete());
    }

    public Optional<User> getUser() {
        return Optional.of(DiscordUtils.getMainGuild().getJDA().retrieveUserById(discordId).complete());
    }
}
