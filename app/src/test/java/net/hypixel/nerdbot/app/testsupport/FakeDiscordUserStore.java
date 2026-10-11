package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory {@link DiscordUserStore} for tests.
 *
 * <p>Seed users with {@link #seed(DiscordUser)} and inspect which users were cached or saved with
 * {@link #cachedIds()} and {@link #savedIds()}. Insertion order is preserved so tests can assert
 * deterministic iteration.
 */
public class FakeDiscordUserStore implements DiscordUserStore {

    private final Map<String, DiscordUser> users = new LinkedHashMap<>();
    private final List<String> cachedIds = new ArrayList<>();
    private final List<String> savedIds = new ArrayList<>();
    private final List<String> clearedDriveAccessIds = new ArrayList<>();
    private final Set<String> failingSaves = new HashSet<>();
    private final Set<String> throwingSaves = new HashSet<>();
    private final Set<String> throwingFinds = new HashSet<>();
    // Users are held by reference, so this snapshot is the only way to tell what was persisted from what is in memory
    private final Map<String, Integer> persistedAnnouncedYears = new HashMap<>();

    public FakeDiscordUserStore seed(DiscordUser user) {
        users.put(user.getDiscordId(), user);
        return this;
    }

    public FakeDiscordUserStore failSavesFor(String... discordIds) {
        failingSaves.addAll(List.of(discordIds));
        return this;
    }

    public FakeDiscordUserStore throwOnSaveFor(String... discordIds) {
        throwingSaves.addAll(List.of(discordIds));
        return this;
    }

    public FakeDiscordUserStore throwOnFindFor(String... discordIds) {
        throwingFinds.addAll(List.of(discordIds));
        return this;
    }

    @Override
    public List<DiscordUser> getAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public List<DiscordUser> getAllFromDatabase() {
        return getAll();
    }

    @Override
    public Optional<DiscordUser> findById(String discordId) {
        if (throwingFinds.contains(discordId)) {
            throw new IllegalStateException("find failed");
        }
        return Optional.ofNullable(users.get(discordId));
    }

    @Override
    public List<String> findIdsWithBirthdaySet() {
        return users.values().stream()
            .filter(user -> user.getBirthdayData() != null && user.getBirthdayData().isBirthdaySet())
            .map(DiscordUser::getDiscordId)
            .toList();
    }

    @Override
    public void cache(DiscordUser user) {
        users.put(user.getDiscordId(), user);
        cachedIds.add(user.getDiscordId());
    }

    @Override
    public CompletableFuture<Void> save(DiscordUser user) {
        if (throwingSaves.contains(user.getDiscordId())) {
            throw new IllegalStateException("save threw");
        }
        users.put(user.getDiscordId(), user);
        if (failingSaves.contains(user.getDiscordId())) {
            return CompletableFuture.failedFuture(new IllegalStateException("save failed"));
        }
        savedIds.add(user.getDiscordId());
        persistedAnnouncedYears.put(user.getDiscordId(), announcedYearOf(user));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void clearDriveAccess(DiscordUser user) {
        cache(user);
        clearedDriveAccessIds.add(user.getDiscordId());
    }

    public List<String> clearedDriveAccessIds() {
        return List.copyOf(clearedDriveAccessIds);
    }

    public List<String> cachedIds() {
        return List.copyOf(cachedIds);
    }

    public List<String> savedIds() {
        return List.copyOf(savedIds);
    }

    public Integer persistedAnnouncedYear(String discordId) {
        return persistedAnnouncedYears.get(discordId);
    }

    private static Integer announcedYearOf(DiscordUser user) {
        return user.getBirthdayData() == null ? null : user.getBirthdayData().getLastAnnouncedYear();
    }
}
