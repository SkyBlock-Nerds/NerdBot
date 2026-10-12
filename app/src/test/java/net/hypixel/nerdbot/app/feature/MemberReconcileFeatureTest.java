package net.hypixel.nerdbot.app.feature;

import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.feature.MemberReconcileFeature.Change;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberReconcileFeatureTest {

    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final MapBadgeCatalog badges = new MapBadgeCatalog();
    private final NerdBotConfig config = new NerdBotConfig();
    private final MemberReconcileFeature feature = new MemberReconcileFeature(new GuildScope(() -> config), Fakes.of(JDA.class, Map.of()),
        new MemberQueue(users, Runnable::run), users, badges, () -> Fakes.of(BotState.class, Map.of()));

    private static BadgeEntry badge(String id) {
        return new BadgeEntry(id, null, 0L);
    }

    @Test
    void aMemberWithoutARecordGetsOneWithTheirRoles() {
        assertEquals(Change.CREATED, feature.reconcile("1", List.of("10", "11")).join());

        DiscordUser created = users.findById("1").orElseThrow();
        assertEquals(List.of("10", "11"), created.getRoleIds());
        assertNotNull(created.getLastActivity());
        assertEquals(List.of("1"), users.savedIds());
    }

    @Test
    void aRecordAlreadyInStepIsNotSaved() {
        DiscordUser user = new DiscordUser("1");
        user.setRoleIds(List.of("10"));
        users.seed(user);

        assertEquals(Change.UNCHANGED, feature.reconcile("1", List.of("10")).join());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void changedRolesAreStoredAndSaved() {
        DiscordUser user = new DiscordUser("1");
        users.seed(user);

        assertEquals(Change.UPDATED, feature.reconcile("1", List.of("10")).join());
        assertSame(user, users.findById("1").orElseThrow());
        assertEquals(List.of("10"), user.getRoleIds());
        assertEquals(List.of("1"), users.savedIds());
    }

    @Test
    void missingActivityAndBadgesAreFilledIn() {
        DiscordUser user = new DiscordUser("1");
        user.setLastActivity(null);
        user.setBadges(null);
        users.seed(user);

        assertEquals(Change.UPDATED, feature.reconcile("1", List.of()).join());
        assertNotNull(user.getLastActivity());
        assertNotNull(user.getBadges());
    }

    @Test
    void badgesNoLongerConfiguredAreDropped() {
        badges.with(new Badge("known", "Known", null));
        DiscordUser user = new DiscordUser("1");
        user.setBadges(new ArrayList<>(List.of(badge("known"), badge("retired"))));
        users.seed(user);

        assertEquals(Change.UPDATED, feature.reconcile("1", List.of()).join());
        assertEquals(List.of("known"), user.getBadges().stream().map(BadgeEntry::badgeId).toList());
    }

    @Test
    void withNoBadgesConfiguredNoneAreDropped() {
        DiscordUser user = new DiscordUser("1");
        user.setBadges(new ArrayList<>(List.of(badge("any"))));
        users.seed(user);

        assertEquals(Change.UNCHANGED, feature.reconcile("1", List.of()).join());
        assertEquals(1, user.getBadges().size());
    }

    @Test
    void aFailedLookupLeavesTheRecordAlone() {
        users.throwOnFindFor("1");

        assertThrows(RuntimeException.class, () -> feature.reconcile("1", List.of("10")).join());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void withoutTheGuildNothingIsReconciled() {
        feature.reconcileAll();

        assertTrue(users.savedIds().isEmpty());
    }
}
