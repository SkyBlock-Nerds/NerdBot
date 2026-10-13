package net.hypixel.nerdbot.app.user;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.inject.BotState;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.testsupport.BirthdayFixtures;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirthdayAnnouncementFeatureTest {

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final CallRecorder recorder = new CallRecorder();
    private final TextChannel channel = recorder.fake(TextChannel.class, Map.of("getId", "70"));
    private final Map<String, Member> members = new HashMap<>();
    private final Guild guild = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID,
        "getMemberById", (CallRecorder.Answer) args -> members.get(String.valueOf(args[0]))));
    private final JDA jda = Fakes.of(JDA.class, Map.of(
        "getGuilds", List.of(guild),
        "getTextChannelById", (CallRecorder.Answer) args -> "70".equals(args[0]) ? channel : null));
    private final BirthdayAnnouncementFeature feature = new BirthdayAnnouncementFeature(users, new GuildScope(() -> config), jda, () -> config,
        () -> Fakes.of(BotState.class, Map.of()), Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getChannelConfig().setBirthdayNotificationChannelId("70");
    }

    private void birthdayToday(String id) {
        users.seed(BirthdayFixtures.member(id, LocalDate.of(2000, 10, 12), "UTC"));
    }

    @Test
    void membersInTheGuildAreWished() {
        birthdayToday("1");
        members.put("1", Fakes.of(Member.class, Map.of("getId", "1")));

        feature.announceDueBirthdays();

        assertEquals("Happy birthday <@1>!", recorder.last("sendMessage").arg(0));
    }

    @Test
    void someoneWhoLeftIsNotWished() {
        birthdayToday("1");

        feature.announceDueBirthdays();

        assertFalse(recorder.called("sendMessage"));
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void withoutTheChannelNobodyIsMarkedAsAnnounced() {
        birthdayToday("1");
        members.put("1", Fakes.of(Member.class, Map.of("getId", "1")));
        config.getChannelConfig().setBirthdayNotificationChannelId("");

        feature.announceDueBirthdays();

        assertTrue(users.savedIds().isEmpty());
    }
}
