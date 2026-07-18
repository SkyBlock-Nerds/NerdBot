package net.hypixel.nerdbot.app.feature;

import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selecting which users are due for a Mojang-profile refresh: only those with an assigned profile
 * whose cache has gone stale.
 */
class StaleProfileSelectionTest {

    private static final long TTL_HOURS = 24;

    @Test
    void selectsOnlyAssignedProfilesThatAreStale() {
        DiscordUser stale = userWithProfile("stale", 0L);              // lastUpdated far in the past -> due
        DiscordUser fresh = userWithProfile("fresh", System.currentTimeMillis()); // just updated -> not due
        DiscordUser unassigned = new DiscordUser("unassigned");        // default profile has no UUID

        FakeDiscordUserStore store = new FakeDiscordUserStore().seed(stale).seed(fresh).seed(unassigned);

        List<DiscordUser> due = ProfileUpdateFeature.profilesRequiringUpdate(store, TTL_HOURS);

        assertEquals(List.of("stale"), due.stream().map(DiscordUser::getDiscordId).toList());
    }

    @Test
    void selectionIsEmptyWhenNoProfilesAreAssigned() {
        FakeDiscordUserStore store = new FakeDiscordUserStore()
            .seed(new DiscordUser("a"))
            .seed(new DiscordUser("b"));

        assertTrue(ProfileUpdateFeature.profilesRequiringUpdate(store, TTL_HOURS).isEmpty());
    }

    private static DiscordUser userWithProfile(String discordId, long lastUpdated) {
        DiscordUser user = new DiscordUser(discordId);
        MojangProfile profile = new MojangProfile();
        profile.setUniqueId(UUID.randomUUID());
        profile.setUsername("Username");
        profile.setLastUpdated(lastUpdated);
        user.setMojangProfile(profile);
        return user;
    }
}
