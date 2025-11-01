package net.hypixel.nerdbot.discord.storage.database.model.user;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.discord.storage.badge.Badge;
import net.hypixel.nerdbot.discord.storage.badge.TieredBadge;
import net.hypixel.nerdbot.discord.storage.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.discord.storage.database.model.user.birthday.BirthdayData;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.MojangProfile;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@AllArgsConstructor
@Getter
@Setter
@Slf4j
public class DiscordUser {

    private String discordId;
    private List<BadgeEntry> badges;
    private LastActivity lastActivity;
    private BirthdayData birthdayData;
    private MojangProfile mojangProfile;

    public DiscordUser() {
    }

    public DiscordUser(String discordId) {
        this(discordId, new ArrayList<>(), new LastActivity(), new BirthdayData(), new MojangProfile());
    }

    public boolean isProfileAssigned() {
        return this.mojangProfile != null && this.mojangProfile.getUniqueId() != null;
    }

    public boolean noProfileAssigned() {
        return !this.isProfileAssigned();
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
        badges.removeIf(badgeEntry -> badgeEntry.badgeId().equals(badge.getId()));
        log.debug("Removed existing tiered badge for " + discordId + " with ID " + badge.getId() + " and tier " + tier);

        if (tier > 0 && tier <= badge.getTiers().size()) {
            return badges.add(new BadgeEntry(badge.getId(), tier));
        } else {
            throw new IllegalArgumentException("Invalid tier for tiered badge");
        }
    }

    public boolean hasBadge(Badge badge) {
        return badges.stream().map(BadgeEntry::badgeId).anyMatch(s -> s.equals(badge.getId()));
    }

    public boolean hasBadge(TieredBadge badge, int tier) {
        return badges.stream().anyMatch(badgeEntry -> badgeEntry.badgeId().equals(badge.getId()) && badgeEntry.tier() == tier);
    }

    public boolean removeBadge(Badge badge) {
        return badges.removeIf(badgeEntry -> badgeEntry.badgeId().equals(badge.getId()));
    }

    public boolean removeBadge(TieredBadge badge, int tier) {
        return badges.removeIf(badgeEntry -> badgeEntry.badgeId().equals(badge.getId()) && badgeEntry.tier() == tier);
    }
}