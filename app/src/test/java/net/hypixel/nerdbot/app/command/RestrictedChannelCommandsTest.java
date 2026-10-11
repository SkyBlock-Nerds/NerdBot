package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.hypixel.nerdbot.app.channel.RestrictedChannelGroups;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.ChannelFakes;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictedChannelCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    private final ChannelFakes discord = new ChannelFakes();
    private final FakeConfigStore store = new FakeConfigStore(new NerdBotConfig());
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final RestrictedChannelGroups groups = new RestrictedChannelGroups(store::current, store);
    private final RestrictedChannelCommands commands = new RestrictedChannelCommands(store::current, store, groups, users, Runnable::run, new MutableClock());
    private final Role staff = discord.addRole("10", "Staff");

    @BeforeEach
    void setUp() {
        channelConfig().setAutoManageRoleRestrictedChannels(true);
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void togglingFlipsAndSaves() {
        commands.toggle(slash());

        assertFalse(channelConfig().isAutoManageRoleRestrictedChannels());
        assertEquals(1, store.saves());
        assertTrue(DiscordFakes.lastEditText(recorder).contains("**disabled**"));
    }

    @Test
    void aToggleThatCantBeSavedIsUndone() {
        store.failWrites();

        commands.toggle(slash());

        assertTrue(channelConfig().isAutoManageRoleRestrictedChannels());
        assertEquals(ConfigCommands.SAVE_FAILED_MESSAGE, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void statusCountsGroupsAndChannels() {
        groups.refresh(discord.hiddenChannel("100", staff));
        groups.refresh(discord.hiddenChannel("101", staff));

        commands.status(slash());

        MessageEmbed embed = embed();
        assertEquals("✅ Enabled", field(embed, "Automatic Management"));
        assertEquals("1", field(embed, "Configured Groups"));
        assertEquals("2", field(embed, "Total Tracked Channels"));
    }

    @Test
    void rebuildIsRefusedWhileManagementIsOff() {
        channelConfig().setAutoManageRoleRestrictedChannels(false);

        commands.rebuild(slash()).join();

        assertEquals("❌ " + RestrictedChannelCommands.MANAGEMENT_DISABLED, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void rebuildScansTheGuildAndSummarises() {
        discord.hiddenChannel("100", staff);
        discord.publicChannel("101");

        commands.rebuild(slash()).join();

        String summary = DiscordFakes.lastEditText(recorder);
        assertTrue(summary.contains("**Before:** 0 groups, 0 channels"), summary);
        assertTrue(summary.contains("**After:** 1 groups, 1 channels"), summary);
        assertTrue(summary.contains("- **Staff Channels** (1 channels)"), summary);
    }

    @Test
    void aLongRebuildSummaryStaysUnderDiscordsLimit() {
        List<RoleRestrictedChannelGroup> many = IntStream.range(0, 200)
            .mapToObj(index -> new RoleRestrictedChannelGroup("g" + index, "A rather long group display name number " + index, new String[]{"1"}, new String[]{"2"}, 1, 1, 1, 1, true))
            .toList();

        String summary = RestrictedChannelCommands.rebuildSummary(new RestrictedChannelGroups.Rebuild(0, 0, many));

        assertTrue(summary.length() <= 2000);
        assertTrue(summary.matches("(?s).*\\.\\.\\. and \\d+ more"), summary);
    }

    @Test
    void cleaningDropsEmptyGroupsAndSaves() {
        channelConfig().setRoleRestrictedChannelGroups(new ArrayList<>(List.of(group("empty", new String[0]), group("full", new String[]{"1"}))));

        commands.clean(slash());

        assertEquals(1, channelConfig().getRoleRestrictedChannelGroups().size());
        assertEquals(1, store.saves());
        assertEquals("✅ Removed 1 empty role-restricted channel group(s). 1 groups remaining.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void cleaningWithNothingEmptySavesNothing() {
        commands.clean(slash());

        assertEquals("✅ No empty role-restricted channel groups found.", DiscordFakes.lastEditText(recorder));
        assertEquals(0, store.saves());
    }

    @Test
    void scanningShowsGroupsBeforeAndAfter() {
        commands.scan(slash(), discord.hiddenChannel("100", staff));

        MessageEmbed embed = embed();
        assertEquals("No groups", field(embed, "Before Scan"));
        assertEquals("Staff Channels", field(embed, "After Scan"));
        assertTrue(field(embed, "Permission Analysis").contains("Allowed roles: Staff"));
    }

    @Test
    void untrackedChannelsCantBeScanned() {
        commands.scan(slash(), discord.textChannel("100", ChannelType.NEWS, true, List.of(staff), null));

        assertEquals("Only text, voice and forum channels are put in role-restricted groups.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void scanningNeedsManagementOn() {
        channelConfig().setAutoManageRoleRestrictedChannels(false);

        commands.scan(slash(), discord.hiddenChannel("100", staff));

        assertEquals(RestrictedChannelCommands.MANAGEMENT_DISABLED, DiscordFakes.lastEditText(recorder));
    }

    @Test
    void activityDebugShowsAccessAndRequirements() {
        groups.refresh(discord.hiddenChannel("100", staff));
        users.seed(new DiscordUser("200"));

        commands.debugActivity(slash(), member("200", staff));

        String value = embed().getFields().getFirst().getValue();
        assertTrue(value.startsWith("**Has Access:** ✅"), value);
        assertTrue(value.contains("**Requirements Met:** 0/3 ⚠️"), value);
    }

    @Test
    void withoutAccessTheRequiredRolesAreListed() {
        groups.refresh(discord.hiddenChannel("100", staff));
        users.seed(new DiscordUser("200"));

        commands.debugActivity(slash(), member("200"));

        assertEquals("**Has Access:** ❌\n**Required Roles:** Staff", embed().getFields().getFirst().getValue());
    }

    @Test
    void activityDebugForSomeoneWithoutARecordSaysSo() {
        groups.refresh(discord.hiddenChannel("100", staff));

        commands.debugActivity(slash(), member("200"));

        assertEquals("<@200> has no user record.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void manyGroupsStayWithinTheFieldLimit() {
        channelConfig().setRoleRestrictedChannelGroups(new ArrayList<>(IntStream.range(0, 30).mapToObj(index -> group("g" + index, new String[]{"1"})).toList()));
        users.seed(new DiscordUser("200"));

        commands.debugActivity(slash(), member("200"));

        List<MessageEmbed.Field> fields = embed().getFields();
        assertEquals(MessageEmbed.MAX_FIELD_AMOUNT, fields.size());
        assertEquals("6 more groups aren't shown", fields.getLast().getValue());
    }

    @Test
    void requirementsCountTowardsTheCheckWindow() {
        RoleRestrictedChannelGroup group = group("staff", new String[]{"1"});
        group.setRequiredRoleIds(new String[]{"10"});
        group.setMinimumMessagesForActivity(0);
        group.setMinimumVotesForActivity(0);

        String value = RestrictedChannelCommands.groupActivity(group, new LastActivity(), Set.of("10"), discord.guild());

        assertTrue(value.endsWith("**Requirements Met:** 2/3 ✅"), value);
    }

    @Test
    void ungroupedChannelsAreListedByType() {
        groups.refresh(discord.hiddenChannel("100", staff));
        discord.publicChannel("101");
        discord.textChannel("102", ChannelType.VOICE, false, List.of(), null);

        commands.listUngrouped(slash());

        MessageEmbed embed = embed();
        assertEquals("- <#101> (Public)", field(embed, "💬 Text Channels (1)"));
        assertEquals("- channel-102 (Public)", field(embed, "🔊 Voice Channels (1)"));
        assertTrue(field(embed, "📊 Summary").contains("**Grouped:** 1"));
    }

    @Test
    void longChannelListsAreCut() {
        List<GuildChannel> channels = new ArrayList<>();
        IntStream.range(0, 12).forEach(index -> channels.add(discord.publicChannel(String.valueOf(index))));
        EmbedBuilder embed = new EmbedBuilder();

        RestrictedChannelCommands.addChannelList(embed, "Text", channels, 10, channel -> channel.getId());

        assertTrue(embed.getFields().getFirst().getValue().endsWith("... and 2 more"));
    }

    private ChannelConfig channelConfig() {
        return store.current().getChannelConfig();
    }

    private MessageEmbed embed() {
        return DiscordFakes.lastCallItems(recorder, "editOriginalEmbeds", MessageEmbed.class).getFirst();
    }

    private static String field(MessageEmbed embed, String name) {
        return embed.getFields().stream().filter(field -> name.equals(field.getName())).findFirst()
            .orElseThrow(() -> new AssertionError("no field " + name + " in " + embed.getFields())).getValue();
    }

    private static RoleRestrictedChannelGroup group(String id, String[] channelIds) {
        return new RoleRestrictedChannelGroup(id, id, channelIds, new String[]{"99"}, 5, 2, 1, 30, true);
    }

    private SlashCommandInteraction slash() {
        Guild guild = discord.guild();
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", DiscordFakes.user("1"), "getGuild", guild,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private Member member(String id, Role... roles) {
        return Fakes.of(Member.class, Map.of("getId", id, "getAsMention", "<@" + id + ">", "getRoles", List.of(roles), "getGuild", discord.guild()));
    }
}
