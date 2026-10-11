package net.hypixel.nerdbot.app.profile;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Finds a Minecraft account by name and, when asked, checks it links back to the member's Discord account on Hypixel. */
public class MojangProfileLookup {

    public sealed interface Result {
    }

    public record Found(MojangProfile profile) implements Result {
    }

    /** The account can't be used; {@code reason} is safe to show the member. */
    public record Rejected(String reason) implements Result {
    }

    private final MinecraftAccounts accounts;

    @Inject
    public MojangProfileLookup(MinecraftAccounts accounts) {
        this.accounts = accounts;
    }

    public CompletableFuture<Result> lookup(String username, String discordName, boolean enforceSocial) {
        return accounts.mojangProfile(username).thenCompose(mojang -> {
            if (mojang.isFailure()) {
                return CompletableFuture.completedFuture(new Rejected("Failed to look up Minecraft profile for `" + username + "`"));
            }

            MojangProfile profile = mojang.orElse(null);
            if (profile != null && profile.getErrorMessage() != null) {
                return CompletableFuture.completedFuture(new Rejected(profile.getErrorMessage()));
            }
            if (profile == null || profile.getUniqueId() == null) {
                return CompletableFuture.completedFuture(new Rejected("Could not find a Minecraft account named `" + username + "`"));
            }

            return accounts.hypixelPlayer(profile.getUniqueId()).thenApply(hypixel -> {
                if (hypixel.isFailure()) {
                    return new Rejected("Unable to look up `" + profile.getUsername() + "` on Hypixel");
                }

                HypixelPlayerResponse response = hypixel.orElse(null);
                if (response == null || !response.isSuccess()) {
                    return new Rejected("Unable to look up `" + profile.getUsername() + "`: "
                        + (response == null ? "no response from Hypixel" : response.getCause()));
                }

                if (enforceSocial) {
                    Optional<String> problem = socialProblem(discordName, profile.getUsername(), response.getPlayer());
                    if (problem.isPresent()) {
                        return new Rejected(problem.get());
                    }
                }

                return new Found(profile);
            });
        });
    }

    private static Optional<String> socialProblem(String discordName, String minecraftUsername, @Nullable HypixelPlayerResponse.Player player) {
        if (player == null) {
            return Optional.of("`" + minecraftUsername + "` has never joined Hypixel, so their linked Discord account can't be checked!");
        }

        HypixelPlayerResponse.SocialMedia socialMedia = player.getSocialMedia();
        Map<HypixelPlayerResponse.SocialMedia.Service, String> links = socialMedia == null ? null : socialMedia.getLinks();
        String linkedDiscord = links == null ? null : links.get(HypixelPlayerResponse.SocialMedia.Service.DISCORD);

        return socialVerificationError(discordName, linkedDiscord, minecraftUsername, socialMedia != null);
    }

    static Optional<String> socialVerificationError(String discordName, @Nullable String linkedDiscord, String minecraftUsername, boolean socialMediaPresent) {
        if (!socialMediaPresent) {
            return Optional.of("The Hypixel profile for `" + minecraftUsername + "` does not have any social media linked!");
        }

        if (linkedDiscord == null) {
            return Optional.of("The Hypixel profile for `" + minecraftUsername + "` does not have a Discord account linked!");
        }

        if (!discordName.equalsIgnoreCase(linkedDiscord)) {
            return Optional.of("The Discord account `" + discordName + "` does not match the social media linked on the Hypixel profile for `" + minecraftUsername + "`! It is currently set to `" + linkedDiscord + "`");
        }

        return Optional.empty();
    }
}
