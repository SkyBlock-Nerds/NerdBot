package net.hypixel.nerdbot.app.activity;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.ChannelActivityEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.member;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.role;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.forumParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textChannel;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.textParent;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.thread;
import static net.hypixel.nerdbot.app.testsupport.ThreadFakes.voiceChannel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityListenerTest {

    private static final String SUGGESTIONS = "500";
    private static final String ALPHA_SUGGESTIONS = "501";
    private static final String PROJECT_SUGGESTIONS = "502";
    private static final String GROUP_FORUM = "600";
    private static final String GROUP_ROLE = "60";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final GuildScope guildScope = new GuildScope(() -> config);
    private final MemberQueue queue = new MemberQueue(users, Runnable::run);
    private ActivityListener listener;
    private LastActivity activity;

    @BeforeEach
    void setUp() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getSuggestionConfig().setForumChannelId(SUGGESTIONS);
        config.getAlphaProjectConfig().setAlphaForumIds(new String[]{ALPHA_SUGGESTIONS});
        config.getAlphaProjectConfig().setProjectForumIds(new String[]{PROJECT_SUGGESTIONS});
        config.getChannelConfig().setProjectChannelNames(new String[]{"project"});
        RoleRestrictedChannelGroup group = new RoleRestrictedChannelGroup();
        group.setIdentifier("staff");
        group.setChannelIds(new String[]{GROUP_FORUM});
        group.setRequiredRoleIds(new String[]{GROUP_ROLE});
        config.getChannelConfig().setRoleRestrictedChannelGroups(List.of(group));

        DiscordUser user = new DiscordUser("1");
        users.seed(user);
        activity = user.getLastActivity();
        listener = listener(List.of());
    }

    private ActivityListener listener(List<Guild> guilds) {
        return new ActivityListener(guildScope, queue, () -> config, clock, Fakes.of(JDA.class, Map.of("getGuilds", guilds)));
    }

    private static List<String> channelIds(LastActivity activity) {
        return activity.getChannelActivityHistory().stream().map(ChannelActivityEntry::getChannelId).toList();
    }

    @Nested
    class Messages {

        @Test
        void aMessageRecordsGlobalActivityAndTheChannel() {
            listener.recordMessage(member("1"), textChannel("10", "general"), false).join();

            assertEquals(clock.millis(), activity.getLastGlobalActivity());
            assertEquals(List.of("10"), channelIds(activity));
            assertEquals(-1L, activity.getLastAlphaActivity());
        }

        @Test
        void alphaAndProjectChannelsAreRecognisedByName() {
            listener.recordMessage(member("1"), textChannel("10", "alpha-chat"), false).join();
            listener.recordMessage(member("1"), textChannel("11", "project-chat"), false).join();

            assertEquals(clock.millis(), activity.getLastAlphaActivity());
            assertEquals(clock.millis(), activity.getLastProjectActivity());
        }

        @Test
        void blacklistedChannelsAreIgnored() {
            config.getChannelConfig().setBlacklistedChannels(new String[]{"10"});

            listener.recordMessage(member("1"), textChannel("10", "general"), false).join();

            assertEquals(-1L, activity.getLastGlobalActivity());
            assertTrue(activity.getChannelActivityHistory().isEmpty());
        }

        @Test
        void threadsInABlacklistedChannelAreIgnored() {
            config.getChannelConfig().setBlacklistedChannels(new String[]{"10"});

            listener.recordMessage(member("1"), thread("20", textParent("10", "general")), false).join();

            assertEquals(-1L, activity.getLastGlobalActivity());
        }

        @Test
        void aCommentOnASuggestionCountsTowardsTheForumAndItsHistory() {
            listener.recordMessage(member("1"), thread("20", forumParent(SUGGESTIONS, "suggestions")), false).join();

            assertEquals(List.of(clock.millis()), activity.getSuggestionCommentHistory());
            assertEquals(List.of(SUGGESTIONS), channelIds(activity));
        }

        @Test
        void theSuggestionItselfIsNotAComment() {
            listener.recordMessage(member("1"), thread("20", forumParent(SUGGESTIONS, "suggestions")), true).join();

            assertTrue(activity.getSuggestionCommentHistory().isEmpty());
            assertEquals(clock.millis(), activity.getLastGlobalActivity());
        }

        @Test
        void commentsOnAlphaAndProjectSuggestionsAreKeptApart() {
            listener.recordMessage(member("1"), thread("20", forumParent(ALPHA_SUGGESTIONS, "alpha-suggestions")), false).join();
            listener.recordMessage(member("1"), thread("21", forumParent(PROJECT_SUGGESTIONS, "project-suggestions")), false).join();

            assertEquals(1, activity.getAlphaSuggestionCommentHistory().size());
            assertEquals(1, activity.getProjectSuggestionCommentHistory().size());
            assertTrue(activity.getSuggestionCommentHistory().isEmpty());
            assertEquals(clock.millis(), activity.getLastAlphaActivity());
            assertEquals(clock.millis(), activity.getLastProjectActivity());
        }

        @Test
        void aReplyInATextChannelThreadIsNotASuggestionComment() {
            listener.recordMessage(member("1"), thread("20", textParent("10", "general")), false).join();

            assertTrue(activity.getSuggestionCommentHistory().isEmpty());
            assertEquals(List.of("10"), channelIds(activity));
        }

        @Test
        void aThreadInAnAlphaTextChannelCountsAsAlpha() {
            listener.recordMessage(member("1"), thread("20", textParent("10", "alpha-chat")), false).join();

            assertEquals(clock.millis(), activity.getLastAlphaActivity());
        }

        @Test
        void inARestrictedGroupTheGroupGetsTheActivity() {
            listener.recordMessage(member("1", role(GROUP_ROLE)), thread("20", forumParent(GROUP_FORUM, "staff-forum")), false).join();

            assertEquals(1, activity.getRoleRestrictedChannelMessageHistory().get("staff").size());
            assertEquals(1, activity.getRoleRestrictedChannelCommentHistory().get("staff").size());
            assertTrue(activity.getChannelActivityHistory().isEmpty());
        }

        @Test
        void withoutTheGroupsRoleItsAnOrdinaryChannel() {
            listener.recordMessage(member("1"), thread("20", forumParent(GROUP_FORUM, "staff-forum")), false).join();

            assertTrue(activity.getRoleRestrictedChannelMessageHistory().isEmpty());
            assertEquals(List.of(GROUP_FORUM), channelIds(activity));
        }

        @Test
        void aMemberWithoutARecordIsSkipped() {
            listener.recordMessage(member("2"), textChannel("10", "general"), false).join();

            assertTrue(users.findById("2").isEmpty());
        }
    }

    @Nested
    class Suggestions {

        @Test
        void aNewSuggestionIsRecordedForItsOwner() {
            listener.recordSuggestionCreated("1", thread("20", forumParent(SUGGESTIONS, "suggestions"))).join();

            assertEquals(List.of(clock.millis()), activity.getSuggestionCreationHistory());
        }

        @Test
        void anAlphaSuggestionAlsoCountsAsAlphaActivity() {
            listener.recordSuggestionCreated("1", thread("20", forumParent(ALPHA_SUGGESTIONS, "alpha-suggestions"))).join();

            assertEquals(1, activity.getAlphaSuggestionCreationHistory().size());
            assertEquals(clock.millis(), activity.getLastAlphaActivity());
        }

        @Test
        void aProjectSuggestionAlsoCountsAsProjectActivity() {
            listener.recordSuggestionCreated("1", thread("20", forumParent(PROJECT_SUGGESTIONS, "project-suggestions"))).join();

            assertEquals(1, activity.getProjectSuggestionCreationHistory().size());
            assertEquals(clock.millis(), activity.getLastProjectActivity());
        }

        @Test
        void threadsOutsideTheSuggestionForumsAreNotSuggestions() {
            listener.recordSuggestionCreated("1", thread("20", forumParent("999", "alpha-lookalike"))).join();
            listener.recordSuggestionCreated("1", thread("21", textParent("10", "general"))).join();

            assertTrue(activity.getSuggestionCreationHistory().isEmpty());
            assertTrue(activity.getAlphaSuggestionCreationHistory().isEmpty());
        }
    }

    @Nested
    class Votes {

        @Test
        void aVoteIsRecordedOncePerSuggestion() {
            ThreadChannel suggestion = thread("20", forumParent(SUGGESTIONS, "suggestions"));
            long firstVote = clock.millis();

            listener.recordVote(member("1"), suggestion).join();
            clock.advance(Duration.ofMinutes(5));
            listener.recordVote(member("1"), suggestion).join();

            assertEquals(Map.of("20", firstVote), activity.getSuggestionVoteHistoryMap());
        }

        @Test
        void alphaAndProjectVotesAreKeptApart() {
            listener.recordVote(member("1"), thread("20", forumParent(ALPHA_SUGGESTIONS, "alpha"))).join();
            listener.recordVote(member("1"), thread("21", forumParent(PROJECT_SUGGESTIONS, "project"))).join();

            assertEquals(List.of("20"), List.copyOf(activity.getAlphaSuggestionVoteHistoryMap().keySet()));
            assertEquals(List.of("21"), List.copyOf(activity.getProjectSuggestionVoteHistoryMap().keySet()));
            assertTrue(activity.getSuggestionVoteHistoryMap().isEmpty());
        }

        @Test
        void aVoteInARestrictedGroupsForumCountsForTheGroup() {
            listener.recordVote(member("1", role(GROUP_ROLE)), thread("20", forumParent(GROUP_FORUM, "staff-forum"))).join();

            assertEquals(Map.of("20", clock.millis()), activity.getRoleRestrictedChannelVoteHistory().get("staff"));
        }

        @Test
        void votesElsewhereAreNotRecorded() {
            listener.recordVote(member("1"), thread("20", forumParent("999", "elsewhere"))).join();

            assertTrue(activity.getSuggestionVoteHistoryMap().isEmpty());
            assertTrue(activity.getRoleRestrictedChannelVoteHistory().isEmpty());
        }
    }

    @Nested
    class Voice {

        private final AudioChannel lounge = voiceChannel("30", "lounge");

        @Test
        void aLongEnoughSessionIsRecordedWhenTheMemberLeaves() {
            listener.recordVoiceUpdate(member("1"), null, lounge).join();
            clock.advance(Duration.ofMinutes(2));
            listener.recordVoiceUpdate(member("1"), lounge, null).join();

            assertEquals(clock.millis(), activity.getLastVoiceChannelJoinDate());
        }

        @Test
        void aShortSessionIsNotRecorded() {
            listener.recordVoiceUpdate(member("1"), null, lounge).join();
            clock.advance(Duration.ofSeconds(config.getVoiceThreshold()));
            listener.recordVoiceUpdate(member("1"), lounge, null).join();

            assertEquals(-1L, activity.getLastVoiceChannelJoinDate());
        }

        @Test
        void alphaAndProjectVoiceChannelsAreRecognisedByName() {
            AudioChannel alpha = voiceChannel("31", "alpha-voice");
            AudioChannel project = voiceChannel("32", "project-voice");

            listener.recordVoiceUpdate(member("1"), null, alpha).join();
            clock.advance(Duration.ofMinutes(2));
            listener.recordVoiceUpdate(member("1"), alpha, project).join();
            clock.advance(Duration.ofMinutes(2));
            listener.recordVoiceUpdate(member("1"), project, null).join();

            assertEquals(clock.millis() - Duration.ofMinutes(2).toMillis(), activity.getAlphaVoiceJoinDate());
            assertEquals(clock.millis(), activity.getProjectVoiceJoinDate());
            assertEquals(-1L, activity.getLastVoiceChannelJoinDate());
        }

        @Test
        void leavingWithoutAKnownJoinIsNotRecorded() {
            clock.advance(Duration.ofMinutes(2));
            listener.recordVoiceUpdate(member("1"), lounge, null).join();

            assertEquals(-1L, activity.getLastVoiceChannelJoinDate());
        }

        @Test
        void aSessionInARestrictedGroupsChannelCountsForTheGroup() {
            AudioChannel staffVoice = voiceChannel(GROUP_FORUM, "staff-voice");
            Member member = member("1", role(GROUP_ROLE));

            listener.recordVoiceUpdate(member, null, staffVoice).join();
            clock.advance(Duration.ofMinutes(2));
            listener.recordVoiceUpdate(member, staffVoice, null).join();

            assertEquals(Map.of("staff", clock.millis()), activity.getRoleRestrictedChannelLastActivity());
        }

        @Test
        void membersAlreadyInVoiceAtStartupAreTimedFromThen() {
            GuildVoiceState inVoice = Fakes.of(GuildVoiceState.class, Map.of("inAudioChannel", true, "getMember", member("1")));
            GuildVoiceState notInVoice = Fakes.of(GuildVoiceState.class, Map.of("inAudioChannel", false, "getMember", member("2")));
            Guild guild = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID, "getVoiceStates", List.of(inVoice, notInVoice)));
            ActivityListener started = listener(List.of(guild));

            clock.advance(Duration.ofMinutes(2));
            started.recordVoiceUpdate(member("1"), lounge, null).join();

            assertEquals(clock.millis(), activity.getLastVoiceChannelJoinDate());
        }

        @Test
        void otherGuildsVoiceStatesAreIgnoredAtStartup() {
            GuildVoiceState inVoice = Fakes.of(GuildVoiceState.class, Map.of("inAudioChannel", true, "getMember", member("1")));
            Guild otherGuild = Fakes.of(Guild.class, Map.of("getId", "200", "getVoiceStates", List.of(inVoice)));
            ActivityListener started = listener(List.of(otherGuild));

            clock.advance(Duration.ofMinutes(2));
            started.recordVoiceUpdate(member("1"), lounge, null).join();

            assertEquals(-1L, activity.getLastVoiceChannelJoinDate());
        }
    }

    @Test
    void textParentsAreNotTreatedAsForums() {
        IThreadContainerUnion parent = textParent(SUGGESTIONS, "suggestions-chat");

        listener.recordMessage(member("1"), thread("20", parent), false).join();

        assertTrue(activity.getSuggestionCommentHistory().isEmpty());
    }
}
