package net.hypixel.nerdbot.app.user;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.birthday.BirthdayData;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@Slf4j
public class BirthdayAnnouncer {

    public interface Messenger {
        CompletableFuture<?> send(String message);
    }

    private final DiscordUserStore store;
    private final Messenger messenger;
    private final Clock clock;
    private final Predicate<String> stillMember;

    /** For when everyone with a record is in the guild. */
    public BirthdayAnnouncer(DiscordUserStore store, Messenger messenger, Clock clock) {
        this(store, messenger, clock, memberId -> true);
    }

    /**
     * @param stillMember whether a member is still in the guild; someone who left while the bot was
     *                    down keeps their record, and isn't wished a happy birthday
     */
    public BirthdayAnnouncer(DiscordUserStore store, Messenger messenger, Clock clock, Predicate<String> stillMember) {
        this.store = store;
        this.messenger = messenger;
        this.clock = clock;
        this.stillMember = stillMember;
    }

    public int announceDue() {
        Instant now = clock.instant();
        int started = 0;

        for (String id : store.findIdsWithBirthdaySet()) {
            if (!stillMember.test(id)) {
                log.debug("Not announcing the birthday of {}; they're no longer in the guild", id);
                continue;
            }

            try {
                Optional<DiscordUser> found = store.findById(id);
                if (found.isEmpty()) {
                    continue;
                }

                DiscordUser user = found.get();
                if (isDue(user.getBirthdayData(), now)) {
                    announce(user, LocalDate.ofInstant(now, user.getBirthdayData().getTimeZoneId()));
                    started++;
                }
            } catch (RuntimeException exception) {
                log.error("Failed to announce the birthday of {}", id, exception);
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
        String message = message(user, today);

        data.setLastAnnouncedYear(today.getYear());
        try {
            store.save(user).join();
        } catch (RuntimeException exception) {
            data.setLastAnnouncedYear(previousYear);
            throw exception;
        }

        deliver(message).whenComplete((ignored, failure) -> {
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

    private CompletableFuture<?> deliver(String message) {
        try {
            CompletableFuture<?> delivery = messenger.send(message);
            return delivery != null
                ? delivery
                : CompletableFuture.failedFuture(new IllegalStateException("Messenger returned no delivery future"));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}