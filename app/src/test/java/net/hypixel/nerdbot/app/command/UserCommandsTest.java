package net.hypixel.nerdbot.app.command;

import com.google.gson.Gson;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.profile.MojangProfileLookup;
import net.hypixel.nerdbot.app.profile.ProfileEmbeds;
import net.hypixel.nerdbot.app.profile.ProfileLinker;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCommandsTest {

    private static final UUID NOTCH_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final Map<String, MojangProfile> mojang = new HashMap<>();
    private String linkedDiscord = "alex";
    private List<Member> guildMembers = List.of();

    private final MinecraftAccounts accounts = new MinecraftAccounts() {
        @Override
        public CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username) {
            MojangProfile profile = mojang.get(username);
            return CompletableFuture.completedFuture(profile == null ? Result.failure(new HttpException("404")) : Result.success(profile));
        }

        @Override
        public CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId) {
            return CompletableFuture.completedFuture(Result.success(new Gson().fromJson(
                "{\"success\":true,\"player\":{\"socialMedia\":{\"links\":{\"DISCORD\":\"" + linkedDiscord + "\"}}}}", HypixelPlayerResponse.class)));
        }
    };

    private final UserCommands commands = new UserCommands(users, new MojangProfileLookup(accounts), new ProfileLinker(users, () -> config),
        new ProfileEmbeds(() -> config, new MapBadgeCatalog()), accounts, logged::add, Runnable::run);

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void aModeratorCanLinkAProfile() {
        DiscordUser user = seed("100");
        mojang.put("Notch", profile("Notch", NOTCH_ID));

        commands.link(slash(), member("100", "alex"), "Notch", false).join();

        assertEquals("Updated profile for <@100> to Notch (UUID: " + NOTCH_ID + ")", DiscordFakes.lastEditText(recorder));
        assertEquals("Notch", user.getMojangProfile().getUsername());
        assertEquals(List.of("100"), users.savedIds());
        assertEquals("Mojang Profile Change", logged.getFirst().getTitle());
    }

    @Test
    void aMismatchIsCalledOut() {
        seed("100");
        mojang.put("Notch", profile("Notch", NOTCH_ID));
        linkedDiscord = "herobrine";

        commands.link(slash(), member("100", "alex"), "Notch", false).join();

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("Profile mismatch error for Notch: The Discord account `alex` does not match"));
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void theSocialCheckCanBeBypassed() {
        seed("100");
        mojang.put("Notch", profile("Notch", NOTCH_ID));
        linkedDiscord = "herobrine";

        commands.link(slash(), member("100", "alex"), "Notch", true).join();

        assertEquals(List.of("100"), users.savedIds());
    }

    @Test
    void linkingSomeoneWithoutARecordSaysSo() {
        commands.link(slash(), member("100", "alex"), "Notch", false).join();

        assertEquals("<@100> has no user record.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void membersWithARecordButNoProfileAreListed() {
        seed("100");
        seed("101").setMojangProfile(profile("Linked", NOTCH_ID));
        guildMembers = List.of(member("100", "alex"), member("101", "sam"), member("102", "no-record"), bot("103"));
        seed("103");

        commands.listMissing(slash());

        MessageEmbed embed = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals("Missing Mojang Profiles (1)", embed.getTitle());
        assertEquals("<@100>", embed.getDescription());
    }

    @Test
    void nobodyMissingSaysSo() {
        guildMembers = List.of(member("100", "alex"));
        seed("100").setMojangProfile(profile("Linked", NOTCH_ID));

        commands.listMissing(slash());

        assertEquals("No users with missing Mojang Profiles found.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aLongListIsCutWithACount() {
        List<String> mentions = IntStream.range(0, 300).mapToObj(index -> "<@" + (100_000_000_000_000_000L + index) + ">").toList();

        String joined = UserCommands.joinWithinLimit(mentions, MessageEmbed.DESCRIPTION_MAX_LENGTH);

        assertTrue(joined.length() <= MessageEmbed.DESCRIPTION_MAX_LENGTH);
        assertTrue(joined.matches(".* and \\d+ more"), joined);
        assertEquals("a, b", UserCommands.joinWithinLimit(List.of("a", "b"), 100));
    }

    @Test
    void infoShowsProfileBadgesThenActivity() {
        seed("100");

        commands.showInfo(slash(), member("100", "alex"));

        List<MessageEmbed> shown = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class);
        assertEquals("alex's Badges", shown.get(1).getTitle());
        assertEquals("General Activity", shown.get(2).getTitle());
        assertEquals(6, shown.size());
    }

    @Test
    void infoForSomeoneWithoutARecordSaysSo() {
        commands.showInfo(slash(), member("100", "alex"));

        assertEquals("<@100> has no user record.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void badgesAreShownForTheMember() {
        seed("100");

        commands.showBadges(slash(), member("100", "alex"));

        assertEquals("alex's Badges", DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst().getTitle());
    }

    @Test
    void legacyNamesAreMigratedAndSaved() {
        DiscordUser legacy = seed("100");
        seed("101").setMojangProfile(profile("Already", NOTCH_ID));
        mojang.put("Notch", profile("Notch", NOTCH_ID));
        guildMembers = List.of(member("100", "Notch"), member("101", "Already"), member("102", "Herobrine"));
        seed("102");

        commands.migrateNames(slash());

        assertEquals("Notch", legacy.getMojangProfile().getUsername());
        assertEquals(List.of("100"), users.savedIds());
        assertEquals("Migration complete! Migrated 1 users. 1 couldn't be migrated; see the logs.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aNameMojangDoesntKnowIsNotSaved() {
        seed("100");
        MojangProfile missing = new MojangProfile();
        missing.setErrorMessage("Couldn't find any profile with name Notch");
        mojang.put("Notch", missing);
        guildMembers = List.of(member("100", "Notch"));

        commands.migrateNames(slash());

        assertTrue(users.savedIds().isEmpty());
        assertEquals("Migration complete! Migrated 0 users. 1 couldn't be migrated; see the logs.", DiscordFakes.lastEditText(recorder));
    }

    private DiscordUser seed(String id) {
        DiscordUser user = new DiscordUser(id);
        users.seed(user);
        return user;
    }

    private SlashCommandInteraction slash() {
        Task<?>[] loaded = new Task<?>[1];
        loaded[0] = Fakes.of(Task.class, Map.of(
            "onSuccess", (CallRecorder.Answer) args -> {
                consumer(args[0]).accept(guildMembers);
                return loaded[0];
            },
            "onError", (CallRecorder.Answer) args -> loaded[0]));
        Guild guild = Fakes.of(Guild.class, Map.of("loadMembers", loaded[0]));
        return recorder.fake(SlashCommandInteraction.class, Map.of(
            "getUser", Fakes.of(User.class, Map.of("getId", "1", "getAsMention", "<@1>")),
            "getGuild", guild,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private Member member(String id, String name) {
        return memberFor(id, name, false);
    }

    private Member bot(String id) {
        return memberFor(id, "bot", true);
    }

    private Member memberFor(String id, String name, boolean isBot) {
        return Fakes.of(Member.class, Map.of(
            "getId", id,
            "getEffectiveName", name,
            "getAsMention", "<@" + id + ">",
            "getUser", Fakes.of(User.class, Map.of("getId", id, "getName", name, "isBot", isBot)),
            "getGuild", Fakes.of(Guild.class, Map.of()),
            "modifyNickname", (CallRecorder.Answer) args -> Fakes.of(AuditableRestAction.class, Map.of())));
    }

    private static MojangProfile profile(String name, UUID id) {
        MojangProfile profile = new MojangProfile();
        profile.setUsername(name);
        profile.setUniqueId(id);
        return profile;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }
}
