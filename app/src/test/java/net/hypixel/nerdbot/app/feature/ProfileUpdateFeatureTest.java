package net.hypixel.nerdbot.app.feature;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.feature.ProfileUpdateFeature.Outcome;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileUpdateFeatureTest {

    private static final long TTL_HOURS = 12;

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final Map<String, Result<MojangProfile, HttpException>> profiles = new HashMap<>();
    private final CallRecorder recorder = new CallRecorder();
    private final Map<String, Member> members = new HashMap<>();
    private final Guild guild = Fakes.of(Guild.class, Map.of("getId", "100",
        "getMemberById", (CallRecorder.Answer) args -> members.get(String.valueOf(args[0]))));
    private final MinecraftAccounts accounts = new MinecraftAccounts() {
        @Override
        public CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username) {
            Result<MojangProfile, HttpException> result = profiles.get(username);
            if (result == null) {
                throw new IllegalStateException("Mojang is down");
            }
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId) {
            throw new UnsupportedOperationException();
        }
    };
    private final ProfileUpdateFeature feature = new ProfileUpdateFeature(users, new MemberQueue(users, Runnable::run), accounts,
        new GuildScope(() -> config), Fakes.of(JDA.class, Map.of()), () -> config, () -> Fakes.of(BotState.class, Map.of()));

    private DiscordUser linked(String id, String name, long lastUpdated) {
        MojangProfile profile = new MojangProfile();
        profile.setUniqueId(UUID.nameUUIDFromBytes(id.getBytes()));
        profile.setUsername(name);
        profile.setLastUpdated(lastUpdated);
        DiscordUser user = new DiscordUser(id);
        user.setMojangProfile(profile);
        users.seed(user);
        return user;
    }

    private void mojangReturns(DiscordUser user, String name) {
        MojangProfile profile = new MojangProfile();
        profile.setUniqueId(user.getMojangProfile().getUniqueId());
        profile.setUsername(name);
        profiles.put(user.getMojangProfile().getUniqueId().toString(), Result.success(profile));
    }

    private Member member(String id, String name) {
        Member member = recorder.fake(Member.class, Map.of("getId", id, "getEffectiveName", name));
        members.put(id, member);
        return member;
    }

    @Test
    void aStaleProfileIsRefreshedSavedAndTheNicknameUpdated() {
        DiscordUser user = linked("1", "OldName", 0);
        mojangReturns(user, "NewName");
        member("1", "OldName");

        assertEquals(Outcome.REFRESHED, feature.refresh("1", TTL_HOURS, guild));

        assertEquals("NewName", user.getMojangProfile().getUsername());
        assertEquals(List.of("1"), users.savedIds());
        assertEquals("NewName", recorder.last("modifyNickname").arg(1));
    }

    @Test
    void aNicknameAlreadyHoldingTheNameIsLeftAlone() {
        DiscordUser user = linked("1", "Name", 0);
        mojangReturns(user, "Name");
        member("1", "[Admin] name");

        feature.refresh("1", TTL_HOURS, guild);

        assertFalse(recorder.called("modifyNickname"));
    }

    @Test
    void aFreshProfileIsSkipped() {
        linked("1", "Name", System.currentTimeMillis());

        assertEquals(Outcome.SKIPPED, feature.refresh("1", TTL_HOURS, guild));
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void anUnlinkedMemberIsSkipped() {
        users.seed(new DiscordUser("1"));

        assertEquals(Outcome.SKIPPED, feature.refresh("1", TTL_HOURS, guild));
    }

    @Test
    void aFailedLookupLeavesTheProfileAsItWas() {
        DiscordUser user = linked("1", "OldName", 0);
        profiles.put(user.getMojangProfile().getUniqueId().toString(), Result.failure(new HttpException("rate limited")));

        assertEquals(Outcome.FAILED, feature.refresh("1", TTL_HOURS, guild));
        assertEquals("OldName", user.getMojangProfile().getUsername());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void oneMemberFailingDoesNotStopTheRest() {
        linked("1", "Broken", 0);
        DiscordUser working = linked("2", "OldName", 0);
        mojangReturns(working, "NewName");

        assertEquals(Outcome.FAILED, feature.refresh("1", TTL_HOURS, guild));
        assertEquals(Outcome.REFRESHED, feature.refresh("2", TTL_HOURS, guild));
        assertEquals("NewName", working.getMojangProfile().getUsername());
    }

    @Test
    void aMemberWhoLeftStillHasTheirProfileRefreshed() {
        DiscordUser user = linked("1", "OldName", 0);
        mojangReturns(user, "NewName");

        assertEquals(Outcome.REFRESHED, feature.refresh("1", TTL_HOURS, Fakes.of(Guild.class, Map.of(
            "retrieveMemberById", (CallRecorder.Answer) args -> {
                throw new IllegalStateException("no member");
            }))));
        assertEquals("NewName", user.getMojangProfile().getUsername());
    }

    @Test
    void theRefreshNeverRunsMoreThanHourly() {
        config.setMojangUsernameCacheTTL(0);

        assertEquals(3_600_000L, feature.defaultPeriodMs(config));
    }
}
