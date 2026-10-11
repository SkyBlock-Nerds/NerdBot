package net.hypixel.nerdbot.app.command;

import com.google.gson.Gson;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.profile.MinecraftAccounts;
import net.hypixel.nerdbot.app.profile.MojangProfileLookup;
import net.hypixel.nerdbot.app.profile.PendingVerifications;
import net.hypixel.nerdbot.app.profile.ProfileEmbeds;
import net.hypixel.nerdbot.app.profile.ProfileLinker;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.app.user.BirthdayService;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCommandsTest {

    private static final String MEMBER = "100";
    private static final String GUILD = "1";
    private static final String VERIFY_LOG = "50";
    private static final UUID NOTCH_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final MutableClock clock = new MutableClock();
    private final PendingVerifications pending = new PendingVerifications(clock);
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final List<Member> kicked = new ArrayList<>();
    private String linkedDiscord = "alex";
    private int lookups;
    private boolean memberInServer = true;
    private Guild guild;
    private TextChannel verifyChannel;
    private final Map<String, Object> jdaAnswers = new HashMap<>();
    private Member member;

    private final MojangProfileLookup lookup = new MojangProfileLookup(new MinecraftAccounts() {
        @Override
        public CompletableFuture<Result<MojangProfile, HttpException>> mojangProfile(String username) {
            lookups++;
            return CompletableFuture.completedFuture(Result.success(profile()));
        }

        @Override
        public CompletableFuture<Result<HypixelPlayerResponse, HttpException>> hypixelPlayer(UUID uniqueId) {
            return CompletableFuture.completedFuture(Result.success(new Gson().fromJson(
                "{\"success\":true,\"player\":{\"socialMedia\":{\"links\":{\"DISCORD\":\"" + linkedDiscord + "\"}}}}", HypixelPlayerResponse.class)));
        }
    });

    // /profile suggestions only hands over to SuggestionListings, which has its own tests
    private final ProfileCommands commands = new ProfileCommands(users, () -> config, Fakes.of(JDA.class, jdaAnswers), lookup,
        new ProfileLinker(users, () -> config), pending, new ProfileEmbeds(() -> config, new MapBadgeCatalog()), null,
        new BirthdayService(users, clock), logged::add, Runnable::run);

    @BeforeEach
    void setUp() {
        config.setGuildId(GUILD);
        config.getChannelConfig().setVerifyLogChannelId(VERIFY_LOG);
        config.getRoleConfig().setModeratorRoleId("7");

        verifyChannel = recorder.fake(TextChannel.class, Map.of("sendMessage", (CallRecorder.Answer) args -> recorder.fake(MessageCreateAction.class)));
        member = member();
        guild = Fakes.of(Guild.class, Map.of(
            "retrieveMemberById", (CallRecorder.Answer) args -> Fakes.of(CacheRestAction.class, Map.of("submit", (CallRecorder.Answer) submitArgs ->
                memberInServer ? CompletableFuture.completedFuture(member) : CompletableFuture.failedFuture(unknownMember()))),
            "kick", (CallRecorder.Answer) args -> {
                kicked.add((Member) args[0]);
                return Fakes.of(AuditableRestAction.class, Map.of("submit", (CallRecorder.Answer) submitArgs -> CompletableFuture.completedFuture(null)));
            }));
        jdaAnswers.put("getGuildById", (CallRecorder.Answer) args -> GUILD.equals(args[0]) ? guild : null);
        jdaAnswers.put("getTextChannelById", (CallRecorder.Answer) args -> VERIFY_LOG.equals(args[0]) ? verifyChannel : null);
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void aPendingRequestIsNotSentAgain() {
        seedMember();
        pending.submit(MEMBER, profile());

        commands.requestVerification(slash(), "Notch").join();

        assertEquals(ProfileCommands.STILL_PENDING_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertEquals(0, lookups);
    }

    @Test
    void aDeclinedMemberIsToldSo() {
        seedMember();
        pending.decline(MEMBER);

        commands.requestVerification(slash(), "Notch").join();

        assertEquals(ProfileCommands.DECLINED_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertEquals(0, lookups);
    }

    @Test
    void verifyingWithoutARecordSaysTheProfileCantLoad() {
        commands.requestVerification(slash(), "Notch").join();

        assertEquals(ProfileCommands.NO_RECORD_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void aRejectedAccountGivesTheReason() {
        seedMember();
        linkedDiscord = "herobrine";

        commands.requestVerification(slash(), "Notch").join();

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("The Discord account `alex` does not match"));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void aMatchingAccountIsSentToModerators() {
        seedMember();

        commands.requestVerification(slash(), "Notch").join();

        assertEquals(ProfileCommands.REQUEST_SENT_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
        assertEquals("<@&7>", recorder.last("sendMessage").arg(0));
        assertEquals(List.of("verification-accept-100", "verification-deny-100"),
            DiscordFakes.lastCallItems(recorder, "addActionRow", Button.class).stream().map(Button::getId).toList());
    }

    @Test
    void withoutAVerifyLogChannelTheRequestIsStillRecorded() {
        seedMember();
        config.getChannelConfig().setVerifyLogChannelId("");

        commands.requestVerification(slash(), "Notch").join();

        assertEquals(ProfileCommands.REQUEST_SENT_MESSAGE, DiscordFakes.lastEditText(recorder));
        assertFalse(recorder.called("sendMessage"));
    }

    @Test
    void aVerifiedMemberCanChangeTheirProfile() {
        DiscordUser user = seedMember();
        user.setMojangProfile(profile("Old", UUID.randomUUID()));

        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals("Updated your Mojang profile to Notch (`" + NOTCH_ID + "`)", DiscordFakes.lastEditText(recorder));
        assertEquals("Notch", user.getMojangProfile().getUsername());
        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals("Mojang Profile Link", logged.getFirst().getTitle());
    }

    @Test
    void anUnverifiedMemberMustVerifyFirst() {
        seedMember();

        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals("You have not yet verified your account. Please do so before attempting to change it.", DiscordFakes.lastEditText(recorder));
        assertEquals(0, lookups);
    }

    @Test
    void changingAProfileOutsideTheServerIsRefused() {
        memberInServer = false;

        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals(ProfileCommands.NOT_IN_SERVER_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void withoutTheGuildTheChangeFailsSafely() {
        config.setGuildId("404");

        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals("Failed to link the profile. Please try again later.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void changingWithoutARecordSaysTheProfileCantLoad() {
        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals(ProfileCommands.NO_RECORD_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aFailedSaveIsReported() {
        DiscordUser user = seedMember();
        user.setMojangProfile(profile("Old", UUID.randomUUID()));
        users.failSavesFor(MEMBER);

        commands.changeLinkedProfile(slash(), "Notch").join();

        assertEquals("Failed to link the profile. Please try again later.", DiscordFakes.lastEditText(recorder));
        assertTrue(logged.isEmpty());
    }

    @Test
    void activityIsShownFromTheRecord() {
        seedMember();

        commands.showActivity(slash());

        assertEquals(4, DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).size());
    }

    @Test
    void activityWithoutARecordSaysTheProfileCantLoad() {
        commands.showActivity(slash());

        assertEquals(ProfileCommands.NO_RECORD_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void theProfileShowsLinkBadgesAndBirthday() {
        DiscordUser user = seedMember();
        user.setMojangProfile(profile());

        commands.showProfile(slash());

        MessageEmbed embed = DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
        assertEquals("Your Profile", embed.getTitle());
        assertEquals(List.of("Notch (" + NOTCH_ID + ")", "None", "Not Set"), embed.getFields().stream().map(MessageEmbed.Field::getValue).toList());
    }

    @Test
    void badgesAreHiddenUnlessShownPublicly() {
        seedMember();

        commands.showBadges(slash(), null);
        assertEquals(true, recorder.calls("deferReply").getFirst().arg(0));

        CallRecorder publicRecorder = new CallRecorder();
        SlashCommandInteraction publicSlash = publicRecorder.fake(SlashCommandInteraction.class, Map.of("getUser", user(), "getMember", member,
            "getHook", publicRecorder.fake(InteractionHook.class, Map.of("isExpired", false))));
        commands.showBadges(publicSlash, true);
        assertEquals(false, publicRecorder.calls("deferReply").getFirst().arg(0));
        assertEquals("Your Badges", DiscordFakes.lastCallItems(publicRecorder, "editOriginalEmbeds", MessageEmbed.class).getFirst().getTitle());
    }

    @Test
    void theHidePreferenceIsSavedStraightAway() {
        DiscordUser user = seedMember();

        commands.changeHidePreference(slash(), true).join();

        assertTrue(user.isAutoHideGenCommands());
        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals("Your default image generator message behavior has been set to hidden", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aHidePreferenceThatWontSaveIsReported() {
        seedMember();
        users.failSavesFor(MEMBER);

        commands.changeHidePreference(slash(), false).join();

        assertEquals("An error occurred while saving your preference, please try again later.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void theHidePreferenceWithoutARecordSaysTheProfileCantLoad() {
        commands.changeHidePreference(slash(), false).join();

        assertEquals(ProfileCommands.NO_RECORD_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void birthdayChangesAnswerWithTheOutcome() {
        commands.changeBirthday(slash(), service -> new BirthdayService.Outcome(BirthdayService.Status.SUCCESS, "Birthday saved"), "Failed").join();

        assertEquals("Birthday saved", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aBirthdayChangeThatThrowsGetsTheFailureMessage() {
        commands.changeBirthday(slash(), service -> {
            throw new IllegalStateException("database down");
        }, "Failed to set the birthday. Please try again later.").join();

        assertEquals("Failed to set the birthday. Please try again later.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void denyingBlocksTheMemberAndOffersAKick() {
        pending.submit(MEMBER, profile());

        commands.handleVerification(button("verification-deny-100")).join();

        assertEquals(PendingVerifications.State.DECLINED, pending.state(MEMBER));
        assertEquals(List.of("Kick (3)", "Denied"), lastButtonLabels("editComponents"));
    }

    @Test
    void eachKickClickCountsDown() {
        commands.handleVerification(button("verification-kick-100-3")).join();

        assertEquals(List.of("Kick (2)", "Denied"), lastButtonLabels("editComponents"));
        assertEquals("verification-kick-100-2", DiscordFakes.lastCallItems(recorder, "editComponents", ActionRow.class)
            .getFirst().getButtons().getFirst().getId());
    }

    @Test
    void theLastKickClickAsksForConfirmation() {
        commands.handleVerification(button("verification-kick-100-1")).join();

        assertEquals("Are you sure you want to kick <@100>?", recorder.last("reply").arg(0));
    }

    @Test
    void anExpiredRequestCantBeAccepted() {
        commands.handleVerification(button("verification-accept-100")).join();

        assertEquals(List.of("Expired"), lastButtonLabels("editComponents"));
        assertEquals(ProfileCommands.EXPIRED_MESSAGE, recorder.last("sendMessage").arg(0));
    }

    @Test
    void acceptingLinksTheProfile() {
        DiscordUser user = seedMember();
        pending.submit(MEMBER, profile());

        commands.handleVerification(button("verification-accept-100")).join();

        assertEquals("Notch", user.getMojangProfile().getUsername());
        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals(List.of("Accepted"), lastButtonLabels("editOriginalComponents"));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void acceptingSomeoneWhoLeftSaysSo() {
        seedMember();
        pending.submit(MEMBER, profile());
        memberInServer = false;

        commands.handleVerification(button("verification-accept-100")).join();

        assertEquals(List.of("Left Server"), lastButtonLabels("editOriginalComponents"));
        assertEquals(PendingVerifications.State.NONE, pending.state(MEMBER));
    }

    @Test
    void aFailedAcceptCanBeTriedAgain() {
        seedMember();
        users.failSavesFor(MEMBER);
        pending.submit(MEMBER, profile());

        commands.handleVerification(button("verification-accept-100")).join();

        assertEquals("Couldn't link the profile. Please try again.", recorder.last("sendMessage").arg(0));
        assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
    }

    @Test
    void acceptingSomeoneWithoutARecordSaysSo() {
        pending.submit(MEMBER, profile());

        commands.handleVerification(button("verification-accept-100")).join();

        assertEquals("That member has no user record, so the profile couldn't be linked.", recorder.last("sendMessage").arg(0));
        assertEquals(PendingVerifications.State.PENDING, pending.state(MEMBER));
    }

    @Test
    void kickingUsesTheirMinecraftNameWhenLinked() {
        seedMember().setMojangProfile(profile());

        commands.handleVerification(button("verification-kicknow-100")).join();

        assertEquals(List.of(member), kicked);
        assertEquals("Notch has been kicked from the server after failing verification.",
            DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst().getDescription());
    }

    @Test
    void kickingSomeoneWhoLeftSaysSo() {
        memberInServer = false;

        commands.handleVerification(button("verification-kicknow-100")).join();

        assertEquals("<@100> has already left the server.", DiscordFakes.lastEditText(recorder));
        assertEquals(List.of(), kicked);
    }

    @Test
    void aButtonWithoutAMemberIsIgnored() {
        commands.handleVerification(button("verification-done")).join();

        assertTrue(recorder.called("deferEdit"));
        assertFalse(recorder.called("editComponents"));
    }

    @Test
    void timezonesMatchTypedTextInOrder() {
        List<String> zones = ProfileCommands.timezoneChoices("LONDON").stream().map(Command.Choice::getName).toList();

        assertEquals(List.of("Europe/London"), zones);
        assertEquals(25, ProfileCommands.timezoneChoices("").size());
        assertNull(ProfileCommands.timezoneChoices("nowhere-at-all").stream().findFirst().orElse(null));
    }

    private DiscordUser seedMember() {
        DiscordUser user = new DiscordUser(MEMBER);
        users.seed(user);
        return user;
    }

    private List<String> lastButtonLabels(String method) {
        return DiscordFakes.lastCallItems(recorder, method, ActionRow.class).stream()
            .flatMap(row -> row.getButtons().stream())
            .map(Button::getLabel)
            .toList();
    }

    private SlashCommandInteraction slash() {
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", user(), "getMember", member,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private ButtonInteraction button(String componentId) {
        return recorder.fake(ButtonInteraction.class, Map.of("getComponentId", componentId, "getGuild", guild,
            "getUser", DiscordFakes.user("7"), "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private static User user() {
        return Fakes.of(User.class, Map.of("getId", MEMBER, "getName", "alex", "getEffectiveName", "alex"));
    }

    private Member member() {
        return recorder.fake(Member.class, Map.of(
            "getId", MEMBER,
            "getUser", user(),
            "getEffectiveName", "alex",
            "getAsMention", "<@100>",
            "getGuild", Fakes.of(Guild.class, Map.of()),
            "modifyNickname", (CallRecorder.Answer) args -> recorder.fake(AuditableRestAction.class)));
    }

    private static MojangProfile profile() {
        return profile("Notch", NOTCH_ID);
    }

    private static MojangProfile profile(String name, UUID id) {
        MojangProfile profile = new MojangProfile();
        profile.setUsername(name);
        profile.setUniqueId(id);
        return profile;
    }

    private static ErrorResponseException unknownMember() {
        return ErrorResponseException.create(ErrorResponse.UNKNOWN_MEMBER, new Response(new Exception("Unknown Member"), Set.of()));
    }
}
