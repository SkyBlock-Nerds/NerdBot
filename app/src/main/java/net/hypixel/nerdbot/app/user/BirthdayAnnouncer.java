package net.hypixel.nerdbot.app.user;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.birthday.BirthdayData;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
public class BirthdayAnnouncer {

    public interface Messenger {
        CompletableFuture<?> send(String message);
    }

    private final DiscordUserStore store;
    private final Messenger messenger;
    private final Clock clock;

    public BirthdayAnnouncer(DiscordUserStore store, Messenger messenger, Clock clock) {
        this.store = store;
        this.messenger = messenger;
        this.clock = clock;
    }

    public int announceDue() {
        Instant now = clock.instant();
        int started = 0;

        for (DiscordUser user : store.findWithBirthdaySet()) {
            try {
                if (isDue(user.getBirthdayData(), now)) {
                    announce(user, LocalDate.ofInstant(now, user.getBirthdayData().getTimeZoneId()));
                    started++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to announce the birthday of {}", user.getDiscordId(), exception);
            }
        }

        return started;
    }

    static boolean isDue(BirthdayData data, Instant now) {
        if (data == null || !data.isBirthdaySet()) {
            return false;
        }

        LocalDate today = LocalDate.ofInstant(now, data.getTimeZoneId());
        return data.getBirthdayIn(today.getYear()).equals(today) && data.getLastAnnouncedYear() != today.getYear();
    }

    static String message(DiscordUser user, LocalDate today) {
        BirthdayData data = user.getBirthdayData();
        String message = "Happy birthday <@" + user.getDiscordId() + ">!";
        return data.isShouldAnnounceAge() ? message + " You are now " + data.getAgeOn(today) + " years old!" : message;
    }

    // Recorded before sending so a crash can never post twice; a rejected message is undone so the next run retries
    private void announce(DiscordUser user, LocalDate today) {
        BirthdayData data = user.getBirthdayData();
        int previousYear = data.getLastAnnouncedYear();

        data.setLastAnnouncedYear(today.getYear());
        try {
            store.save(user).join();
        } catch (CompletionException exception) {
            data.setLastAnnouncedYear(previousYear);
            throw exception;
        }

        messenger.send(message(user, today)).whenComplete((ignored, failure) -> {
            if (failure == null) {
                log.info("Announced the birthday of {}", user.getDiscordId());
                return;
            }

            log.warn("Discord rejected the birthday message for {}, will retry next run", user.getDiscordId(), failure);
            data.setLastAnnouncedYear(previousYear);
            store.save(user).exceptionally(saveFailure -> {
                log.error("Failed to undo the birthday record for {}", user.getDiscordId(), saveFailure);
                return null;
            });
        });
    }
}