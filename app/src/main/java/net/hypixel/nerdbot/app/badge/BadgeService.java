package net.hypixel.nerdbot.app.badge;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.app.storage.DiscordUserStore;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;

import java.util.concurrent.CompletableFuture;

/** Giving and taking badges. Changes are saved straight away rather than waiting for the autosave. */
public class BadgeService {

    public sealed interface Change {

        /** Tier is 0 for a badge without tiers. */
        record Given(Badge badge, int tier) implements Change {
        }

        record Revoked(Badge badge, int tier) implements Change {
        }

        record UnknownBadge(String badgeId) implements Change {
        }

        record InvalidTier(Badge badge, int tiers) implements Change {
        }

        record AlreadyHeld(Badge badge, int tier) implements Change {
        }

        record NotHeld(Badge badge, int tier) implements Change {
        }
    }

    private final DiscordUserStore users;
    private final BadgeCatalog badges;

    @Inject
    public BadgeService(DiscordUserStore users, BadgeCatalog badges) {
        this.users = users;
        this.badges = badges;
    }

    /**
     * @param tier for a tiered badge, below 1 means tier 1; ignored otherwise
     * @return what happened; fails if the change couldn't be saved
     */
    public CompletableFuture<Change> give(String memberId, String badgeId, int tier) {
        Badge badge = badges.find(badgeId).orElse(null);
        if (badge == null) {
            return done(new Change.UnknownBadge(badgeId));
        }

        DiscordUser user = users.require(memberId);

        if (badge instanceof TieredBadge tieredBadge) {
            int wantedTier = Math.max(tier, 1);
            if (wantedTier > tieredBadge.getTiers().size()) {
                return done(new Change.InvalidTier(badge, tieredBadge.getTiers().size()));
            }
            if (user.hasBadge(tieredBadge, wantedTier)) {
                return done(new Change.AlreadyHeld(badge, wantedTier));
            }

            user.addBadge(tieredBadge, wantedTier);
            return saved(user, new Change.Given(badge, wantedTier));
        }

        if (user.hasBadge(badge)) {
            return done(new Change.AlreadyHeld(badge, 0));
        }

        user.addBadge(badge);
        return saved(user, new Change.Given(badge, 0));
    }

    /**
     * @param tier for a tiered badge, below 1 means tier 1; ignored otherwise
     * @return what happened; fails if the change couldn't be saved
     */
    public CompletableFuture<Change> revoke(String memberId, String badgeId, int tier) {
        Badge badge = badges.find(badgeId).orElse(null);
        if (badge == null) {
            return done(new Change.UnknownBadge(badgeId));
        }

        DiscordUser user = users.require(memberId);

        if (badge instanceof TieredBadge tieredBadge) {
            int wantedTier = Math.max(tier, 1);
            if (!user.removeBadge(tieredBadge, wantedTier)) {
                return done(new Change.NotHeld(badge, wantedTier));
            }
            return saved(user, new Change.Revoked(badge, wantedTier));
        }

        if (!user.removeBadge(badge)) {
            return done(new Change.NotHeld(badge, 0));
        }
        return saved(user, new Change.Revoked(badge, 0));
    }

    private CompletableFuture<Change> saved(DiscordUser user, Change change) {
        return users.save(user).thenApply(ignored -> change);
    }

    private static CompletableFuture<Change> done(Change change) {
        return CompletableFuture.completedFuture(change);
    }
}
