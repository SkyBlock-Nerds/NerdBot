package net.hypixel.nerdbot.app.channel;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.ChannelFakes;
import net.hypixel.nerdbot.app.testsupport.FakeConfigStore;
import net.hypixel.nerdbot.discord.config.channel.ChannelConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictedChannelGroupsTest {

    private final ChannelFakes discord = new ChannelFakes();
    private final FakeConfigStore store = new FakeConfigStore(new NerdBotConfig());
    private final RestrictedChannelGroups groups = new RestrictedChannelGroups(store::current, store);
    private final Role staff = discord.addRole("10", "Staff");
    private final Role alpha = discord.addRole("11", "Alpha");

    @BeforeEach
    void setUp() {
        channelConfig().setAutoManageRoleRestrictedChannels(true);
        channelConfig().setDefaultMinimumMessagesForActivity(7);
    }

    @Test
    void aHiddenChannelGetsAGroupNamedAfterItsRoles() {
        assertTrue(groups.refresh(discord.hiddenChannel("100", staff)));

        RoleRestrictedChannelGroup group = onlyGroup();
        assertEquals("staff-channels", group.getIdentifier());
        assertEquals("Staff Channels", group.getDisplayName());
        assertEquals(List.of("100"), List.of(group.getChannelIds()));
        assertEquals(Set.of("10"), Set.of(group.getRequiredRoleIds()));
        assertEquals(7, group.getMinimumMessagesForActivity());
        assertEquals(1, store.saves());
    }

    @Test
    void refreshingAnUnchangedChannelSavesNothing() {
        TextChannel channel = discord.hiddenChannel("100", staff);
        groups.refresh(channel);

        assertFalse(groups.refresh(channel));
        assertEquals(1, store.saves());
    }

    @Test
    void aSecondChannelForTheSameRolesJoinsTheGroup() {
        groups.refresh(discord.hiddenChannel("100", staff));
        groups.refresh(discord.hiddenChannel("101", staff));

        assertEquals(List.of("100", "101"), List.of(onlyGroup().getChannelIds()));
    }

    @Test
    void aChannelMadePublicLeavesAndItsEmptyGroupGoes() {
        groups.refresh(discord.hiddenChannel("100", staff));

        assertTrue(groups.refresh(discord.publicChannel("100")));

        assertTrue(channelConfig().getRoleRestrictedChannelGroups().isEmpty());
    }

    @Test
    void aChannelWhoseRolesChangedMovesGroup() {
        groups.refresh(discord.hiddenChannel("100", staff));
        groups.refresh(discord.hiddenChannel("101", staff));

        groups.refresh(discord.hiddenChannel("100", alpha));

        assertEquals(List.of("101"), channelsOf("staff-channels"));
        assertEquals(List.of("100"), channelsOf("alpha-channels"));
    }

    @Test
    void aChannelThatWasTheOnlyOneInItsGroupStillLandsInTheNewOne() {
        groups.refresh(discord.hiddenChannel("100", staff, alpha));
        groups.refresh(discord.hiddenChannel("100", staff));

        assertEquals(List.of("100"), channelsOf("staff-channels"));
        assertEquals(1, channelConfig().getRoleRestrictedChannelGroups().size());
    }

    @Test
    void aChannelListedInTwoGroupsEndsUpInOne() {
        groups.refresh(discord.hiddenChannel("100", staff));
        groups.refresh(discord.hiddenChannel("101", alpha));
        channelConfig().getRoleRestrictedChannelGroups().get(1).setChannelIds(new String[]{"101", "100"});

        assertTrue(groups.refresh(discord.hiddenChannel("100", staff)));

        assertEquals(List.of("100"), channelsOf("staff-channels"));
        assertEquals(List.of("101"), channelsOf("alpha-channels"));
    }

    @Test
    void rolesOnAHiddenParentCategoryCount() {
        Category category = discord.hiddenCategory("50", alpha);

        groups.refresh(discord.hiddenChannelIn("100", category, staff));

        assertEquals(Set.of("10", "11"), Set.of(onlyGroup().getRequiredRoleIds()));
        assertEquals("Alpha & Staff Channels", onlyGroup().getDisplayName());
    }

    @Test
    void nothingChangesWhileAutoManagementIsOff() {
        channelConfig().setAutoManageRoleRestrictedChannels(false);

        assertFalse(groups.refresh(discord.hiddenChannel("100", staff)));
        assertEquals(0, store.saves());
    }

    @Test
    void untrackedChannelTypesAreIgnored() {
        assertFalse(groups.refresh(discord.textChannel("100", ChannelType.NEWS, true, List.of(staff), null)));
    }

    @Test
    void aDeletedChannelIsForgotten() {
        TextChannel channel = discord.hiddenChannel("100", staff);
        groups.refresh(channel);

        assertTrue(groups.forget(channel));
        assertTrue(channelConfig().getRoleRestrictedChannelGroups().isEmpty());
        assertFalse(groups.forget(channel));
    }

    @Test
    void aSaveThatFailsIsReported() {
        store.failWrites();

        assertThrows(UncheckedIOException.class, () -> groups.refresh(discord.hiddenChannel("100", staff)));
    }

    @Test
    void rebuildRechecksEveryChannelAndSavesOnce() {
        TextChannel stillHidden = discord.hiddenChannel("100", staff);
        TextChannel nowPublic = discord.hiddenChannel("101", staff);
        TextChannel deleted = discord.hiddenChannel("102", alpha);
        groups.refresh(stillHidden);
        groups.refresh(nowPublic);
        groups.refresh(deleted);
        discord.remove(deleted);
        discord.remove(nowPublic);
        discord.publicChannel("101");
        discord.hiddenChannel("103", alpha);
        int savesBefore = store.saves();

        RestrictedChannelGroups.Rebuild rebuild = groups.rebuild(discord.guild());

        assertEquals(2, rebuild.groupsBefore());
        assertEquals(3, rebuild.channelsBefore());
        assertEquals(2, rebuild.groupsAfter().size());
        assertEquals(2, rebuild.channelsAfter());
        assertEquals(List.of("100"), channelsOf("staff-channels"));
        assertEquals(List.of("103"), channelsOf("alpha-channels"));
        assertEquals(savesBefore + 1, store.saves());
    }

    @Test
    void rebuildTidiesGroupsWithMissingFields() {
        RoleRestrictedChannelGroup messy = new RoleRestrictedChannelGroup();
        messy.setIdentifier("old");
        messy.setDisplayName(null);
        messy.setChannelIds(null);
        messy.setRequiredRoleIds(new String[]{"10"});
        channelConfig().setRoleRestrictedChannelGroups(new ArrayList<>(List.of(messy)));
        discord.hiddenChannel("100", staff);

        groups.rebuild(discord.guild());

        RoleRestrictedChannelGroup group = onlyGroup();
        assertEquals("old", group.getDisplayName());
        assertEquals(List.of("100"), List.of(group.getChannelIds()));
    }

    private ChannelConfig channelConfig() {
        return store.current().getChannelConfig();
    }

    private RoleRestrictedChannelGroup onlyGroup() {
        List<RoleRestrictedChannelGroup> all = channelConfig().getRoleRestrictedChannelGroups();
        assertEquals(1, all.size(), "groups: " + all);
        return all.getFirst();
    }

    private List<String> channelsOf(String identifier) {
        return channelConfig().getRoleRestrictedChannelGroups().stream()
            .filter(group -> group.getIdentifier().equals(identifier))
            .findFirst()
            .map(group -> Arrays.asList(group.getChannelIds()))
            .orElseThrow(() -> new AssertionError("no group " + identifier));
    }
}
