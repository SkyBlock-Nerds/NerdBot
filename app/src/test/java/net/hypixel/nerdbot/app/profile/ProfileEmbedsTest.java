package net.hypixel.nerdbot.app.profile;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.MapBadgeCatalog;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileEmbedsTest {

    private static final String BLANK = EmbedBuilder.ZERO_WIDTH_SPACE;
    // Midday UTC so the formatted date is the same in any default time zone the tests run under
    private static final long MARCH_8_2024 = Instant.parse("2024-03-08T12:00:00Z").toEpochMilli();

    private final NerdBotConfig config = new NerdBotConfig();
    private final ProfileEmbeds embeds = new ProfileEmbeds(() -> config, new MapBadgeCatalog().with(new Badge("early", "Early Supporter", null)));
    private final DiscordUser user = new DiscordUser("100");

    @BeforeEach
    void setUp() {
        RoleConfig roles = config.getRoleConfig();
        roles.setDaysRequiredForInactivityCheck(14);
        roles.setMessagesRequiredForInactivityCheck(10);
        roles.setVotesRequiredForInactivityCheck(3);
        roles.setCommentsRequiredForInactivityCheck(2);
        roles.setDaysRequiredForVoteHistory(30);
        roles.setMinimumVotesRequiredForPromotion(20);
        roles.setMinimumCommentsRequiredForPromotion(5);
    }

    @Test
    void activityHasTheGeneralAndThreeSuggestionEmbeds() {
        List<MessageEmbed> activity = embeds.activity(member(), user);

        assertEquals(List.of("General Activity", "Suggestion Activity", "Project Suggestion Activity", "Alpha Suggestion Activity"),
            activity.stream().map(MessageEmbed::getTitle).toList());
        assertEquals("Activity Requirements (per 14 days): 10 messages, 3 votes, 2 comments", activity.getFirst().getFooter().getText());
        assertEquals(List.of("Last Seen", "General Voice Chat", "Item Generator", "Projects", "Project Voice Chat", BLANK,
            "Alpha", "Alpha Voice Chat", BLANK), names(activity.getFirst()));
    }

    @Test
    void eachSuggestionEmbedKeepsItsColourAndWording() {
        List<MessageEmbed> activity = embeds.activity(member(), user);

        assertEquals(List.of(Color.YELLOW, Color.ORANGE, Color.RED), activity.subList(1, 4).stream().map(MessageEmbed::getColor).toList());
        assertEquals(List.of("Last Created", "Last Voted", "Last Commented", "Create History", "Vote History", "Comment History"), names(activity.get(1)));
        assertEquals(List.of("Last Created", "Last Voted", "Last Commented", "Created History", "Voted History", "Commented History"), names(activity.get(2)));
        assertEquals(List.of("Last Created", "Last Voted", "Last Commented", "Create History", "Vote History", "Comment History"), names(activity.get(3)));
        for (MessageEmbed embed : activity.subList(1, 4)) {
            assertEquals("Promotion Requirements (per 30 days): 20 votes, 5 comments", embed.getFooter().getText());
        }
    }

    @Test
    void historyIsCountedOverADayAWeekAndAMonth() {
        long now = System.currentTimeMillis();
        LastActivity lastActivity = user.getLastActivity();
        lastActivity.getProjectSuggestionCreationHistory().addAll(List.of(
            now - Duration.ofHours(2).toMillis(), now - Duration.ofDays(3).toMillis(), now - Duration.ofDays(20).toMillis()));
        lastActivity.getAlphaSuggestionVoteHistoryMap().put("thread", now - Duration.ofDays(5).toMillis());

        List<MessageEmbed> activity = embeds.activity(member(), user);

        assertEquals("24 Hours: 1\n7 Days: 2\n30 Days: 3", activity.get(2).getFields().get(3).getValue());
        assertEquals("24 Hours: 0\n7 Days: 1\n30 Days: 1", activity.get(3).getFields().get(4).getValue());
        assertEquals("24 Hours: 0\n7 Days: 0\n30 Days: 0", activity.get(1).getFields().get(3).getValue());
    }

    @Test
    void restrictedGroupsShowOnlyWhenTheMemberHasARequiredRole() {
        config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(
            group("staff", "Staff", "10"),
            group("alpha", "Alpha Testers", "11"),
            group("secret", "Secret", "99")));

        List<MessageEmbed> activity = embeds.activity(member(role("10"), role("11")), user);

        assertEquals(List.of("Staff Activity", "Alpha Testers Activity"), activity.subList(4, activity.size()).stream().map(MessageEmbed::getTitle).toList());
        assertEquals(List.of(Color.CYAN, Color.MAGENTA), activity.subList(4, 6).stream().map(MessageEmbed::getColor).toList());
        assertEquals(List.of("Last Activity", BLANK, BLANK, "Message History", "Vote History", "Comment History"), names(activity.get(4)));
        assertEquals("Activity Requirements (per 30 days): 5 messages, 2 votes, 1 comments", activity.get(4).getFooter().getText());
    }

    @Test
    void aRecordWithoutActivityStillShows() {
        user.setLastActivity(null);

        assertEquals(4, embeds.activity(member(), user).size());
    }

    @Test
    void periodTotalsAskForOneSevenAndThirtyDays() {
        assertEquals("24 Hours: d1\n7 Days: d7\n30 Days: d30", ProfileEmbeds.periodTotals(days -> "d" + days));
    }

    @Test
    void yourOwnBadgesAreTitledForYou() {
        assertEquals("Your Badges", embeds.badges(member("Alex"), user, true).getTitle());
    }

    @Test
    void someoneElsesBadgesUseTheirName() {
        assertEquals("Alex's Badges", embeds.badges(member("Alex"), user, false).getTitle());
        assertEquals("Chris' Badges", embeds.badges(member("Chris"), user, false).getTitle());
    }

    @Test
    void withoutBadgesThereIsAPicture() {
        MessageEmbed embed = embeds.badges(member("Alex"), user, true);

        assertEquals("No badges :(", embed.getDescription());
        assertEquals("https://i.imgur.com/fs71kmJ.png", embed.getImage().getUrl());
    }

    @Test
    void badgesAreListedNewestFirst() {
        user.getBadges().add(new BadgeEntry("early", null, MARCH_8_2024));
        user.getBadges().add(new BadgeEntry("gone", null, MARCH_8_2024 + 1));

        MessageEmbed embed = embeds.badges(member("Alex"), user, true);

        assertEquals("**gone**\nObtained on March 08, 2024\n\n**Early Supporter**\nObtained on March 08, 2024", embed.getDescription());
        assertNull(embed.getImage());
    }

    @Test
    void aRecordWithoutABadgeListHasNone() {
        user.setBadges(null);

        assertEquals("No badges :(", embeds.badges(member("Alex"), user, true).getDescription());
        assertEquals("None", ProfileEmbeds.badgeCount(user));
    }

    @Test
    void badgeCountCountsEntries() {
        user.getBadges().add(new BadgeEntry("early", null, 1L));

        assertEquals("1", ProfileEmbeds.badgeCount(user));
    }

    @Test
    void theLinkedProfileShowsNameAndId() {
        MojangProfile profile = new MojangProfile();
        profile.setUsername("Notch");
        profile.setUniqueId(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        user.setMojangProfile(profile);

        assertEquals("Notch (069a79f4-44e9-4726-a5be-fca90e38aaf5)", ProfileEmbeds.linkedProfile(user));
        assertEquals("[069a79f4-44e9-4726-a5be-fca90e38aaf5](https://sky.shiiyu.moe/stats/069a79f4-44e9-4726-a5be-fca90e38aaf5)",
            ProfileEmbeds.skyCryptLink(profile));
    }

    @Test
    void noLinkedProfileIsMissingData() {
        assertEquals("*Missing Data*", ProfileEmbeds.linkedProfile(user));
    }

    @Test
    void aMissingBirthdayIsNotSet() {
        assertEquals("Not Set", ProfileEmbeds.birthday(user));
        user.setBirthdayData(null);
        assertEquals("Not Set", ProfileEmbeds.birthday(user));
    }

    private static List<String> names(MessageEmbed embed) {
        return embed.getFields().stream().map(MessageEmbed.Field::getName).toList();
    }

    private static RoleRestrictedChannelGroup group(String id, String name, String roleId) {
        RoleRestrictedChannelGroup group = new RoleRestrictedChannelGroup();
        group.setIdentifier(id);
        group.setDisplayName(name);
        group.setRequiredRoleIds(new String[]{roleId});
        return group;
    }

    private static Role role(String id) {
        return Fakes.of(Role.class, Map.of("getId", id));
    }

    private static Member member(Role... roles) {
        return Fakes.of(Member.class, Map.of("getEffectiveName", "Alex", "getRoles", new ArrayList<>(List.of(roles))));
    }

    private static Member member(String name) {
        return Fakes.of(Member.class, Map.of("getEffectiveName", name));
    }
}
