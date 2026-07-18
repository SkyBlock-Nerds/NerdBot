package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link DiscordUserStore} for tests.
 *
 * <p>Seed users with {@link #seed(DiscordUser)} and inspect which users were re-saved with
 * {@link #savedIds()}. Insertion order is preserved so tests can assert deterministic iteration.
 */
public class FakeDiscordUserStore implements DiscordUserStore {

    private final Map<String, DiscordUser> users = new LinkedHashMap<>();
    private final List<String> savedIds = new ArrayList<>();

    /**
     * Add (or replace) a user in the store without recording it as a {@link #save(DiscordUser)}.
     *
     * @return this store, for chaining
     */
    public FakeDiscordUserStore seed(DiscordUser user) {
        users.put(user.getDiscordId(), user);
        return this;
    }

    @Override
    public List<DiscordUser> getAll() {
        return new ArrayList<>(users.values());
    }

    @Override
    public Optional<DiscordUser> findById(String discordId) {
        return Optional.ofNullable(users.get(discordId));
    }

    @Override
    public void save(DiscordUser user) {
        users.put(user.getDiscordId(), user);
        savedIds.add(user.getDiscordId());
    }

    /**
     * @return the IDs passed to {@link #save(DiscordUser)}, in call order (duplicates preserved)
     */
    public List<String> savedIds() {
        return List.copyOf(savedIds);
    }
}
