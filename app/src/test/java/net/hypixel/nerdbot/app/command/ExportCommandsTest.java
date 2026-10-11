package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.hypixel.nerdbot.app.command.ExportCommands.ActivityFilter;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.role.SpecialRoles;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.FakeGreenlitStore;
import net.hypixel.nerdbot.app.testsupport.FakeSuggestionFactory;
import net.hypixel.nerdbot.app.testsupport.SuggestionFixtures;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.discord.testsupport.ManualScheduledExecutor;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportCommandsTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final FakeGreenlitStore greenlit = new FakeGreenlitStore();
    private final FakeSuggestionFactory factory = new FakeSuggestionFactory(config);
    private final JDA jda = Fakes.of(JDA.class, Map.of());
    private final SuggestionCache cache = new SuggestionCache(new ManualScheduledExecutor(), factory, () -> config, jda, Clock.systemUTC());
    private final ExportCommands commands = new ExportCommands(users, greenlit, cache, factory, () -> config, jda, Runnable::run,
        Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void quotedDoublesQuotesInside() {
        assertEquals("\"say \"\"hi\"\"\"", ExportCommands.quoted("say \"hi\""));
    }

    @Test
    void greenlitCsvSkipsOlderSuggestionsAndEscapesQuotes() {
        GreenlitMessage older = greenlit(1_000, "Old", List.of("Bug"));
        GreenlitMessage newer = greenlit(5_000, "The \"best\" idea", List.of("Item \"rework\"", "QoL"));

        String csv = ExportCommands.greenlitCsv(List.of(older, newer), 2_000).orElseThrow();

        assertTrue(csv.contains("\"Item \"\"rework\"\", QoL\""), csv);
        assertTrue(csv.contains("The \"\"best\"\" idea"), csv);
        assertTrue(!csv.contains("Old"), csv);
    }

    @Test
    void greenlitCsvIsEmptyWhenNothingQualifies() {
        assertEquals(Optional.empty(), ExportCommands.greenlitCsv(List.of(greenlit(1_000, "Old", List.of())), 2_000));
    }

    @Test
    void greenlitExportSendsTheFile() {
        greenlit.loads(CompletableFuture.completedFuture(List.of(greenlit(5_000, "New", List.of()))));

        commands.exportGreenlit(DiscordFakes.slash(recorder, "1"), 0);

        MessageEditData sent = assertInstanceOf(MessageEditData.class, recorder.last("editOriginal").arg(0));
        assertTrue(sent.getFiles().getFirst().getName().startsWith("export-greenlit-"));
    }

    @Test
    void greenlitExportWithNothingSaysSo() {
        commands.exportGreenlit(DiscordFakes.slash(recorder, "1"), 0);

        assertEquals("No suggestions were greenlit", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void greenlitExportFailureIsReported() {
        greenlit.loads(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));

        commands.exportGreenlit(DiscordFakes.slash(recorder, "1"), 0);

        assertEquals(ExportCommands.FILE_FAILED, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void uuidsComeFromLinkedNonBotMembersWithTheRole() {
        UUID linked = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        List<Member> members = List.of(
            member("1", false, "Member"),
            member("2", false, "Member"),
            member("3", true, "Member"),
            member("4", false, "Guest"));
        List<DiscordUser> stored = List.of(linkedUser("1", linked), new DiscordUser("2"), linkedUser("3", UUID.randomUUID()), linkedUser("4", UUID.randomUUID()));

        assertEquals("[\"" + linked + "\"]", ExportCommands.uuidJson(members, stored, "member").orElseThrow().replaceAll("\\s", ""));
    }

    @Test
    void uuidsAreEmptyWhenNobodyQualifies() {
        assertEquals(Optional.empty(), ExportCommands.uuidJson(List.of(member("1", false, "Member")), List.of(), "Member"));
    }

    @Test
    void rolesAreListedInTheOrderAskedIgnoringCase() {
        List<Member> members = List.of(member("1", false, "Admin"), member("2", false, "Member"), member("3", false, "member"));

        assertEquals("Member:\nmember 2\nmember 3\n\nAdmin:\nmember 1\n\n",
            ExportCommands.rolesText(members, "Member", "Admin").orElseThrow());
        assertEquals(Optional.empty(), ExportCommands.rolesText(members, "Nobody"));
    }

    @Test
    void activityFilterFillsInDefaults() {
        config.setInactivityDays(7);
        config.setInactivityMessages(10);

        assertEquals(new ActivityFilter(7, 10, null, false), ActivityFilter.from(0, 0, null, config));
        assertEquals(new ActivityFilter(30, 5, "Member", false), ActivityFilter.from(30, 5, "Member", config));
        assertEquals(new ActivityFilter(7, 10, null, true), ActivityFilter.from(-1, -1, null, config));
    }

    @Test
    void activityExportListsInactiveMembersOnly() {
        ActivityFilter filter = new ActivityFilter(7, 10, null, false);
        DiscordUser active = userWithMessages("1", "chat", 50, NOW.minus(Duration.ofDays(1)));
        DiscordUser quiet = userWithMessages("2", "chat", 2, NOW.minus(Duration.ofDays(1)));
        DiscordUser gone = userWithMessages("3", "chat", 0, NOW.minus(Duration.ofDays(30)));
        Map<String, Member> members = Map.of("1", member("1", false, "Member"), "2", member("2", false, "Member"));

        List<DiscordUser> selected = ExportCommands.selectForActivityExport(List.of(active, quiet, gone), members::get, filter, config, NOW.toEpochMilli());

        assertEquals(List.of("2"), selected.stream().map(DiscordUser::getDiscordId).toList());
    }

    @Test
    void messagesInBlacklistedChannelsDoNotCountAsActive() {
        config.getChannelConfig().setBlacklistedChannels(new String[]{"spam"});
        DiscordUser spammer = userWithMessages("1", "spam", 500, NOW.minus(Duration.ofDays(1)));

        List<DiscordUser> selected = ExportCommands.selectForActivityExport(List.of(spammer), Map.of("1", member("1", false, "Member"))::get,
            new ActivityFilter(7, 10, null, false), config, NOW.toEpochMilli());

        assertEquals(1, selected.size());
    }

    @Test
    void staffAreLeftOutUnlessARoleIsAsked() {
        Member staff = member("1", false, SpecialRoles.NAMES[0]);
        DiscordUser user = new DiscordUser("1");

        assertTrue(ExportCommands.selectForActivityExport(List.of(user), Map.of("1", staff)::get,
            new ActivityFilter(7, 10, null, true), config, NOW.toEpochMilli()).isEmpty());
        assertEquals(1, ExportCommands.selectForActivityExport(List.of(user), Map.of("1", staff)::get,
            new ActivityFilter(7, 10, SpecialRoles.NAMES[0], true), config, NOW.toEpochMilli()).size());
    }

    @Test
    void activityExportReadsEveryStoredUserNotJustTheCache() {
        users.seed(new DiscordUser("1"));
        Member member = member("1", false, "Member");
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", DiscordFakes.user("9"));
        answers.put("getGuild", Fakes.of(Guild.class, Map.of("getMemberById", (CallRecorder.Answer) args -> "1".equals(args[0]) ? member : null)));

        commands.exportActivity(recorder.fake(SlashCommandInteraction.class, answers), -1, -1, null);

        assertTrue(recorder.called("sendFiles"));
    }

    @Test
    void uuidExportLoadsMembersAndSendsTheFile() {
        UUID linked = UUID.randomUUID();
        users.seed(linkedUser("1", linked));

        commands.exportUuids(inGuild(membersTask(List.of(member("1", false, "Member")), null)), null);

        assertTrue(recorder.called("sendFiles"));
    }

    @Test
    void uuidExportWithNobodyLinkedSaysSo() {
        commands.exportUuids(inGuild(membersTask(List.of(member("1", false, "Member")), null)), "Member");

        assertEquals(ExportCommands.NOTHING_TO_EXPORT, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void failingToLoadMembersIsReported() {
        commands.exportRoleMembers(inGuild(membersTask(null, new IllegalStateException("missing intent"))), "Member");

        assertEquals(ExportCommands.FILE_FAILED, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void roleExportSendsTheFile() {
        commands.exportRoleMembers(inGuild(membersTask(List.of(member("1", false, "Member")), null)), "Member");

        assertTrue(recorder.called("sendFiles"));
    }

    @Test
    void aThreadExportThatFailsIsReported() {
        ForumChannel forum = recorder.fake(ForumChannel.class,
            Map.of("getAsMention", "<#10>", "getId", "10", "getName", "suggestions"));

        commands.exportThreads(DiscordFakes.slash(recorder, "1"), forum);

        assertEquals(ExportCommands.FILE_FAILED, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void userSuggestionsAreExportedWithTheirContent() {
        NerdBotConfig suggestionConfig = SuggestionFixtures.config("10");
        FakeSuggestionFactory suggestionFactory = new FakeSuggestionFactory(suggestionConfig);
        ManualScheduledExecutor scheduler = new ManualScheduledExecutor();
        ThreadChannel thread = SuggestionFixtures.thread(recorder, 42, "10");
        JDA threadJda = Fakes.of(JDA.class, Map.of("getThreadChannelById", (CallRecorder.Answer) args -> "42".equals(String.valueOf(args[0])) ? thread : null));
        suggestionFactory.firstMessage("42", SuggestionFixtures.firstMessage(42, 500,
            SuggestionFixtures.NOW));
        SuggestionCache filled = new SuggestionCache(scheduler, suggestionFactory, () -> suggestionConfig, threadJda, Clock.systemUTC());
        filled.requestUpdate(thread);
        scheduler.advance(SuggestionCache.UPDATE_DELAY);
        ExportCommands withSuggestions = new ExportCommands(users, greenlit, filled, suggestionFactory, () -> suggestionConfig, threadJda, Runnable::run, Clock.systemUTC());

        withSuggestions.exportSuggestionsBy(DiscordFakes.slash(recorder, "1"), "500");

        assertEquals("Successfully exported 1 suggestions for user ID 500", DiscordFakes.lastEditText(recorder));
        assertTrue(recorder.called("setFiles"));
    }

    @Test
    void userSuggestionsNeedAValidId() {
        commands.exportSuggestionsBy(DiscordFakes.slash(recorder, "1"), "not-a-number");

        assertEquals("Please provide a valid user ID", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void userSuggestionsWithNoneSaysSo() {
        commands.exportSuggestionsBy(DiscordFakes.slash(recorder, "1"), "500");

        assertEquals("No suggestions found for user ID: 500", DiscordFakes.lastEditText(recorder));
    }

    /** A members task that has already finished, with the members or the failure given. */
    @SuppressWarnings("unchecked")
    private Task<List<Member>> membersTask(List<Member> members, Throwable failure) {
        Object[] self = new Object[1];
        self[0] = recorder.fake(Task.class, Map.of(
            "onSuccess", (CallRecorder.Answer) args -> {
                if (members != null) {
                    ((Consumer<Object>) args[0]).accept(members);
                }
                return self[0];
            },
            "onError", (CallRecorder.Answer) args -> {
                if (failure != null) {
                    ((Consumer<Object>) args[0]).accept(failure);
                }
                return self[0];
            }));
        return (Task<List<Member>>) self[0];
    }

    private SlashCommandInteraction inGuild(Task<List<Member>> members) {
        Guild guild = Fakes.of(Guild.class, Map.of("loadMembers", members));
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", DiscordFakes.user("9"), "getGuild", guild));
    }

    private static GreenlitMessage greenlit(long timestamp, String title, List<String> tags) {
        GreenlitMessage message = new GreenlitMessage();
        message.setSuggestionTimestamp(timestamp);
        message.setSuggestionTitle(title);
        message.setSuggestionUrl("https://discord.com/channels/1/2");
        message.setTags(tags);
        return message;
    }

    private static Member member(String id, boolean bot, String... roleNames) {
        List<Role> roles = Arrays.stream(roleNames).map(name -> Fakes.of(Role.class, Map.of("getName", name, "getId", "r-" + name))).toList();
        return Fakes.of(Member.class, Map.of(
            "getId", id,
            "getEffectiveName", "member " + id,
            "getUser", Fakes.of(User.class, Map.of("isBot", bot, "getName", "user" + id)),
            "getRoles", roles));
    }

    private static DiscordUser linkedUser(String id, UUID uuid) {
        DiscordUser user = new DiscordUser(id);
        MojangProfile profile = new MojangProfile();
        profile.setUniqueId(uuid);
        profile.setUsername("Player" + id);
        user.setMojangProfile(profile);
        return user;
    }

    private static DiscordUser userWithMessages(String id, String channelId, int messages, Instant lastMessage) {
        DiscordUser user = new DiscordUser(id);
        user.getLastActivity().getChannelActivityHistory().add(new ChannelActivityEntry(channelId, channelId, messages, lastMessage.toEpochMilli(),
            new HashMap<>(Map.of(lastMessage.atZone(ZoneOffset.UTC).getMonthValue() + "-" + lastMessage.atZone(ZoneOffset.UTC).getYear(), messages))));
        return user;
    }
}
