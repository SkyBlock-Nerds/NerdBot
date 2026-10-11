package net.hypixel.nerdbot.app.profile;

import com.google.gson.Gson;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MojangProfileLookupTest {

    private static final UUID NOTCH_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private Result<MojangProfile, HttpException> mojang = Result.success(profile("Notch", NOTCH_ID));
    private Result<HypixelPlayerResponse, HttpException> hypixel = Result.success(player("{\"socialMedia\":{\"links\":{\"DISCORD\":\"notch\"}}}"));

    private final MojangProfileLookup lookup = new MojangProfileLookup(new MinecraftAccounts() {
        @Override
        public CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username) {
            return CompletableFuture.completedFuture(mojang);
        }

        @Override
        public CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId) {
            return CompletableFuture.completedFuture(hypixel);
        }
    });

    @Test
    void aMatchingDiscordAccountIsFoundIgnoringCase() {
        MojangProfile notch = mojang.orElse(null);

        MojangProfileLookup.Result result = lookup.lookup("notch", "Notch", true).join();

        assertSame(notch, ((MojangProfileLookup.Found) result).profile());
    }

    @Test
    void aMojangFailureIsRejected() {
        mojang = Result.failure(new HttpException("timed out"));

        assertRejected("Failed to look up Minecraft profile for `notch`");
    }

    @Test
    void mojangsOwnErrorIsPassedOn() {
        MojangProfile error = new MojangProfile();
        error.setErrorMessage("Couldn't find any profile with name notch");
        mojang = Result.success(error);

        assertRejected("Couldn't find any profile with name notch");
    }

    @Test
    void aProfileWithoutAnIdIsRejected() {
        mojang = Result.success(profile("notch", null));

        assertRejected("Could not find a Minecraft account named `notch`");
    }

    @Test
    void anEmptyMojangAnswerIsRejected() {
        mojang = Result.success(null);

        assertRejected("Could not find a Minecraft account named `notch`");
    }

    @Test
    void aHypixelFailureIsRejected() {
        hypixel = Result.failure(new HttpException("503"));

        assertRejected("Unable to look up `Notch` on Hypixel");
    }

    @Test
    void anUnsuccessfulHypixelAnswerGivesItsCause() {
        hypixel = Result.success(new Gson().fromJson("{\"success\":false,\"cause\":\"Key throttle\"}", HypixelPlayerResponse.class));

        assertRejected("Unable to look up `Notch`: Key throttle");
    }

    @Test
    void someoneWhoNeverJoinedHypixelCantBeChecked() {
        hypixel = Result.success(new Gson().fromJson("{\"success\":true}", HypixelPlayerResponse.class));

        assertRejected("`Notch` has never joined Hypixel, so their linked Discord account can't be checked!");
    }

    @Test
    void noSocialMediaIsRejected() {
        hypixel = Result.success(player("{}"));

        assertRejected("The Hypixel profile for `Notch` does not have any social media linked!");
    }

    @Test
    void socialMediaWithoutDiscordIsRejected() {
        hypixel = Result.success(player("{\"socialMedia\":{\"links\":{\"TWITTER\":\"notch\"}}}"));

        assertRejected("The Hypixel profile for `Notch` does not have a Discord account linked!");
    }

    @Test
    void socialMediaWithoutAnyLinksIsRejected() {
        hypixel = Result.success(player("{\"socialMedia\":{\"links\":null}}"));

        assertRejected("The Hypixel profile for `Notch` does not have a Discord account linked!");
    }

    @Test
    void aDifferentDiscordAccountIsRejected() {
        hypixel = Result.success(player("{\"socialMedia\":{\"links\":{\"DISCORD\":\"herobrine\"}}}"));

        assertRejected("The Discord account `Notch` does not match the social media linked on the Hypixel profile for `Notch`! It is currently set to `herobrine`");
    }

    @Test
    void withoutTheSocialCheckOnlyTheAccountMatters() {
        hypixel = Result.success(new Gson().fromJson("{\"success\":true}", HypixelPlayerResponse.class));

        assertEquals(MojangProfileLookup.Found.class, lookup.lookup("notch", "Notch", false).join().getClass());
    }

    private void assertRejected(String reason) {
        assertEquals(new MojangProfileLookup.Rejected(reason), lookup.lookup("notch", "Notch", true).join());
    }

    private static MojangProfile profile(String name, UUID id) {
        MojangProfile profile = new MojangProfile();
        profile.setUsername(name);
        profile.setUniqueId(id);
        return profile;
    }

    private static HypixelPlayerResponse player(String playerJson) {
        return new Gson().fromJson("{\"success\":true,\"player\":" + playerJson + "}", HypixelPlayerResponse.class);
    }
}
